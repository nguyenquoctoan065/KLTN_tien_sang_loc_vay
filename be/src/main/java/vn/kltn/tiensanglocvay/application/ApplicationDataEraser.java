package vn.kltn.tiensanglocvay.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.document.StorageService;
import vn.kltn.tiensanglocvay.document.StoredFile;

/**
 * Xóa dữ liệu định danh của một hồ sơ, dùng chung cho rút đồng ý phần (b) (6.3) và tác vụ PURGE_RETENTION (7.6):
 * giấy tờ về MISSING, xóa trường trích xuất, giấy đề nghị (có CCCD, địa chỉ), số CCCD, địa chỉ, cảnh báo đối chiếu.
 * Tệp trong kho do người gọi xóa bằng {@link #deleteFiles} **trong cùng giao dịch**, để xóa tệp lỗi thì hoàn tác cả phần DB.
 */
@Component
public class ApplicationDataEraser {

    /** Kết quả xóa của một hồ sơ: số tài liệu khách đã tải (trừ giấy đề nghị hệ thống) và các tệp cần xóa khỏi kho. */
    public record Erased(int documents, List<StoredFile> files) {
    }

    private final ApplicationDocumentRepository docs;
    private final ExtractedFieldRepository fields;
    private final LoanRequestFormRepository forms;
    private final StorageService storage;

    ApplicationDataEraser(ApplicationDocumentRepository docs, ExtractedFieldRepository fields, LoanRequestFormRepository forms,
                          StorageService storage) {
        this.docs = docs;
        this.fields = fields;
        this.forms = forms;
        this.storage = storage;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Erased erase(LoanApplication a) {
        int documents = 0;
        List<StoredFile> files = new ArrayList<>();
        for (ApplicationDocument d : docs.findByApplicationIdOrderById(a.getId())) {
            if (d.getFileName() != null && d.getDocumentTypeId() != DocumentType.SYSTEM_DOC_TYPE) {
                documents++;
            }
            StoredFile stored = d.stored();
            if (stored != null) {
                files.add(stored);
            }
            d.clearFile();
            fields.deleteByDocument(d.getId());
        }
        forms.findByApplicationId(a.getId()).ifPresent(forms::delete);
        a.setNationalId(null);
        a.setAddress(null);
        a.setChecks(List.of(), null);
        return new Erased(documents, files);
    }

    /** Xóa tệp khỏi kho (Cloudinary / cục bộ); ném lỗi của kho ở tệp đầu tiên không xóa được. */
    public void deleteFiles(List<StoredFile> files) {
        for (StoredFile f : files) {
            storage.delete(f);
        }
    }
}
