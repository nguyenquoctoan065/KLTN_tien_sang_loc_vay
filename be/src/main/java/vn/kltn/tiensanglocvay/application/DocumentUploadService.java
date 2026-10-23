package vn.kltn.tiensanglocvay.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.document.DocumentFields;
import vn.kltn.tiensanglocvay.document.CorruptedFileException;
import vn.kltn.tiensanglocvay.document.OcrContext;
import vn.kltn.tiensanglocvay.document.OcrResult;
import vn.kltn.tiensanglocvay.document.OcrService;
import vn.kltn.tiensanglocvay.document.QualityChecker;
import vn.kltn.tiensanglocvay.document.StorageService;
import vn.kltn.tiensanglocvay.document.StoredFile;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * Tải lên một giấy tờ (docs/be-huong-dan.md 5.3): kiểm tra quyền và trạng thái → kiểm tra chất lượng trên bytes nhận được →
 * lưu kho → đọc OCR với chính bytes đó → lưu DB trong một giao dịch. Lưu kho và OCR chạy ngoài giao dịch để không giữ kết nối
 * cơ sở dữ liệu trong lúc gọi dịch vụ ngoài (B4b: Cloudinary, Gemini).
 */
@Service
public class DocumentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);
    static final String MSG_CORRUPTED = "Tệp bị hỏng hoặc không mở được, vui lòng xuất/chụp lại và tải lên lần nữa.";
    static final String MSG_OCR_FAILED = "Chưa đọc được thông tin tự động, vui lòng nhập tay";

    private final ApplicationService applications;
    private final ApplicationSupport support;
    private final ApplicationDocumentRepository docs;
    private final ExtractedFieldRepository fields;
    private final QualityChecker quality;
    private final StorageService storage;
    private final OcrService ocr;
    private final ActivityLogService activity;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;
    private final String folder;
    private final Clock clock;

    public DocumentUploadService(ApplicationService applications, ApplicationSupport support, ApplicationDocumentRepository docs,
                                 ExtractedFieldRepository fields, QualityChecker quality, StorageService storage, OcrService ocr,
                                 ActivityLogService activity, PlatformTransactionManager txManager,
                                 @Value("${app.storage.folder:tien-sang-loc-vay/dev}") String folder, Clock clock) {
        this.applications = applications;
        this.support = support;
        this.docs = docs;
        this.fields = fields;
        this.quality = quality;
        this.storage = storage;
        this.ocr = ocr;
        this.activity = activity;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.folder = folder;
        this.clock = clock;
    }

    /** Thông tin bước 1 cần cho bước 2. */
    private record Prepared(long appId, DocumentType type, int nextVersion, OcrContext.Builder ctx) {
    }

    public Dtos.Application upload(String id, long docId, MultipartFile file, String issuedDateRaw, CurrentUser u) {
        // Bước 1: quyền, trạng thái, giấy tờ, tệp (thứ tự như mock).
        Prepared p = readTx.execute(s -> {
            LoanApplication app = applications.editableOwned(id, u, "Hồ sơ đã khóa, không tải thêm được.");
            AppBundle b = support.load(app);
            ApplicationDocument d = b.docs.stream().filter(x -> x.getId() == docId).findFirst().orElse(null);
            if (d == null || d.getDocumentTypeId() == DocumentType.SYSTEM_DOC_TYPE) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giấy tờ.");
            }
            if (file == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Thiếu tệp.");
            }
            return new Prepared(app.getId(), b.types.get(d.getDocumentTypeId()), d.getVersionNo() + 1, ocrContext(b));
        });
        LocalDate issuedDate = parseDate(issuedDateRaw);
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();

        // Bước 2: chất lượng → kho → OCR.
        QualityChecker.Result q = quality.check(fileName, file.getSize(), p.type().getAllowedFormats());
        StoredFile stored = null;
        String status = ApplicationDocument.REJECTED_QUALITY;
        String qualityStatus = q.qualityStatus();
        String message = q.message();
        List<OcrResult.Field> extracted = List.of();
        if (q.ok()) {
            byte[] data = bytes(file);
            try {
                stored = storage.upload(data, "doc-" + docId + "-v" + p.nextVersion() + "." + q.ext().toLowerCase(Locale.ROOT),
                        folder + "/app-" + p.appId());
            } catch (CorruptedFileException ex) {
                // Kho từ chối vì tệp hỏng (Cloudinary "Invalid PDF file"…): giấy tờ không đạt chất lượng, không phải lỗi hệ thống.
                log.info("Kho lưu tệp từ chối giấy tờ #{} vì hỏng: {}", docId, ex.getMessage());
                qualityStatus = QualityChecker.CORRUPTED;
                message = MSG_CORRUPTED;
            }
            if (stored != null) {
                OcrResult r = null;
                try {
                    r = ocr.extract(p.type(), data, mimeType(file, q.ext()), p.ctx().seed(q.kb()));
                } catch (RuntimeException e) {
                    log.warn("Đọc giấy tờ #{} thất bại: {}", docId, e.toString());
                }
                if (r == null) {
                    // Dòng trống để khách nhập tay rồi xác nhận (FE chỉ hiện ô nhập khi có trường).
                    status = ApplicationDocument.UPLOADED;
                    qualityStatus = QualityChecker.OK;
                    message = MSG_OCR_FAILED;
                    extracted = DocumentFields.blank(p.type().getId());
                } else if (!r.readable()) {
                    qualityStatus = QualityChecker.BLURRY;
                    message = r.issue() != null ? r.issue() : "Ảnh có độ phân giải thấp hoặc bị mờ, vui lòng chụp lại rõ nét, đủ bốn góc.";
                } else if (!r.documentMatchesType()) {
                    qualityStatus = "WRONG_TYPE";
                    message = "Tệp không phải " + p.type().getName() + ".";
                } else {
                    status = ApplicationDocument.UPLOADED;
                    qualityStatus = QualityChecker.OK;
                    message = null;
                    extracted = r.fields();
                }
                if (ApplicationDocument.REJECTED_QUALITY.equals(status)) {
                    storage.delete(stored);
                    stored = null;
                }
            }
        }

        // Bước 3: lưu DB; lỗi thì xóa tệp vừa lưu, thành công thì xóa tệp của phiên bản cũ.
        StoredFile saved = stored;
        String finalStatus = status;
        String finalQuality = qualityStatus;
        String finalMessage = message;
        List<OcrResult.Field> finalFields = extracted;
        try {
            return tx.execute(s -> {
                LoanApplication app = applications.editableOwned(id, u, "Hồ sơ đã khóa, không tải thêm được.");
                ApplicationDocument d = docs.findById(docId).filter(x -> x.getApplicationId().equals(app.getId()))
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giấy tờ."));
                StoredFile old = d.stored();
                d.upload(fileName, q.ext(), q.kb(), issuedDate, now(), saved, finalStatus, finalQuality, finalMessage);
                fields.deleteByDocument(d.getId());
                for (OcrResult.Field f : finalFields) {
                    fields.save(new ExtractedField(d.getId(), f.key(), truncate(f.label(), 100), truncate(f.value(), 255), f.confidence()));
                }
                AppBundle b = support.load(app);
                support.dropForm(b);
                applications.changed(b);
                activity.log(u, "Tải giấy tờ", p.type().getName() + " (phiên bản " + d.getVersionNo() + ")", app.getId());
                if (old != null) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            deleteQuietly(old);
                        }
                    });
                }
                return support.view(b, u);
            });
        } catch (RuntimeException e) {
            if (saved != null) {
                deleteQuietly(saved);
            }
            throw e;
        }
    }

    private OcrContext.Builder ocrContext(AppBundle b) {
        LoanApplication a = b.app;
        return new OcrContext.Builder(nz(a.getFullName()), nz(a.getNationalId()), a.getDob() == null ? "" : a.getDob().toString(),
                nz(a.getGender()), nz(a.getAddress()), b.input == null ? null : b.input.dob().toString(),
                b.input == null ? null : b.input.gender().name(), b.input == null ? null : Math.round(b.input.avgIncome()),
                b.input == null ? null : b.input.salaryBank(), b.plan.getAmount().longValueExact());
    }

    private void deleteQuietly(StoredFile f) {
        try {
            storage.delete(f);
        } catch (RuntimeException e) {
            log.warn("Không xóa được tệp {}: {}", f.publicId(), e.toString());
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ.", Map.of("issuedDate", "Ngày không hợp lệ (yyyy-MM-dd)."));
        }
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String mimeType(MultipartFile file, String ext) {
        return switch (ext) {
            case "JPG" -> "image/jpeg";
            case "PNG" -> "image/png";
            case "PDF" -> "application/pdf";
            default -> file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        };
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
