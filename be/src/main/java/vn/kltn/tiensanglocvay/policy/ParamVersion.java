package vn.kltn.tiensanglocvay.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bảng param_version (PHIÊN BẢN THAM SỐ). Chỉ bản DRAFT được sửa (B7); bản ACTIVE/EXPIRED bất biến để lượt sàng lọc cũ
 * tính lại đúng. {@code testResult} là JSON của schema TestRunResult (chạy thử 7.4), service tự chuyển đổi.
 */
@Entity
@Table(name = "param_version")
public class ParamVersion {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "version_no", nullable = false)
    private String versionNo;

    @Column(name = "legal_basis", nullable = false)
    private String legalBasis;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "activated_by")
    private Integer activatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "test_result")
    private String testResult;

    protected ParamVersion() {
    }

    /** Bản nháp mới (sao chép từ một phiên bản khác). */
    public ParamVersion(String versionNo, String legalBasis, Integer createdBy, OffsetDateTime createdAt) {
        this.versionNo = versionNo;
        this.legalBasis = legalBasis;
        this.status = DRAFT;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getLegalBasis() {
        return legalBasis;
    }

    public void setLegalBasis(String legalBasis) {
        this.legalBasis = legalBasis;
    }

    public String getStatus() {
        return status;
    }

    public boolean isDraft() {
        return DRAFT.equals(status);
    }

    public void expire() {
        this.status = EXPIRED;
    }

    public void activate(Integer by, OffsetDateTime at) {
        this.status = ACTIVE;
        this.activatedBy = by;
        this.activatedAt = at;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public Integer getActivatedBy() {
        return activatedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public String getTestResult() {
        return testResult;
    }

    public void setTestResult(String testResult) {
        this.testResult = testResult;
    }
}
