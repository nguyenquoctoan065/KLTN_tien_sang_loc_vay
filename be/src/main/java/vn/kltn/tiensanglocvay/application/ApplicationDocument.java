package vn.kltn.tiensanglocvay.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import vn.kltn.tiensanglocvay.document.StoredFile;

/** Bảng application_document (TÀI LIỆU, một dòng của MB05) cùng các cột V6. */
@Entity
@Table(name = "application_document")
public class ApplicationDocument {

    public static final String MISSING = "MISSING";
    public static final String UPLOADED = "UPLOADED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED_QUALITY = "REJECTED_QUALITY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Column(name = "document_type_id", nullable = false, updatable = false)
    private Integer documentTypeId;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private String status;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_format")
    private String fileFormat;

    /** KB. */
    @Column(name = "file_size")
    private Integer fileSize;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "storage_resource_type")
    private String storageResourceType;

    @Column(name = "storage_version")
    private Long storageVersion;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "quality_status")
    private String qualityStatus;

    @Column(name = "quality_message")
    private String qualityMessage;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "verify_result")
    private String verifyResult;

    @Column(name = "verify_reason")
    private String verifyReason;

    protected ApplicationDocument() {
    }

    public ApplicationDocument(Long applicationId, Integer documentTypeId, boolean required) {
        this.applicationId = applicationId;
        this.documentTypeId = documentTypeId;
        this.required = required;
        this.status = MISSING;
    }

    /**
     * Một lần tải lên (mock: tăng phiên bản, ghi tên/định dạng/dung lượng, xóa kết quả đối chiếu cũ). {@code stored} rỗng khi
     * tệp không đạt chất lượng (không lưu kho, theo be-huong-dan 5.3).
     */
    void upload(String fileName, String format, int kb, LocalDate issuedDate, OffsetDateTime at, StoredFile stored, String status,
                String qualityStatus, String qualityMessage) {
        this.fileName = fileName;
        this.fileFormat = format;
        this.fileSize = kb;
        this.versionNo += 1;
        this.uploadedAt = at;
        this.issuedDate = issuedDate;
        this.verifyResult = null;
        this.verifyReason = null;
        this.storagePath = stored == null ? null : stored.publicId();
        this.storageResourceType = stored == null ? null : stored.resourceType();
        this.storageVersion = stored == null ? null : stored.version();
        this.status = status;
        this.qualityStatus = qualityStatus;
        this.qualityMessage = qualityMessage;
    }

    /** Giấy đề nghị vay vốn hệ thống tự lập khi nộp (mock: {@code <code>-giay-de-nghi.pdf}, 120 KB). */
    void markSystemGenerated(String fileName, OffsetDateTime at) {
        this.status = CONFIRMED;
        this.fileName = fileName;
        this.fileFormat = "PDF";
        this.fileSize = 120;
        this.versionNo += 1;
        this.uploadedAt = at;
        this.qualityStatus = "OK";
    }

    /** Tệp đang lưu trong kho, hoặc null. */
    public StoredFile stored() {
        if (storagePath == null) {
            return null;
        }
        String fmt = fileFormat == null ? "" : fileFormat.toLowerCase(Locale.ROOT);
        return new StoredFile(storagePath, storageResourceType, fmt, storageVersion == null ? 0 : storageVersion, 0);
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Integer getDocumentTypeId() {
        return documentTypeId;
    }

    public boolean isRequired() {
        return required;
    }

    public String getStatus() {
        return status;
    }

    void confirm() {
        this.status = CONFIRMED;
    }

    /** Kết quả đối chiếu gần nhất tại chi nhánh (mock {@code d.verifyResult = x.result; d.verifyReason = x.reason || null}). */
    public void recordVerification(String result, String reason) {
        this.verifyResult = result;
        this.verifyReason = reason;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public Integer getFileSize() {
        return fileSize;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public LocalDate getIssuedDate() {
        return issuedDate;
    }

    public String getQualityStatus() {
        return qualityStatus;
    }

    public String getQualityMessage() {
        return qualityMessage;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    public String getVerifyResult() {
        return verifyResult;
    }

    public String getVerifyReason() {
        return verifyReason;
    }
}
