package vn.kltn.tiensanglocvay.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Bảng param_version (PHIÊN BẢN THAM SỐ). Cột test_result (jsonb) được ánh xạ khi làm chạy thử ở B7. */
@Entity
@Table(name = "param_version")
public class ParamVersion {

    public static final String ACTIVE = "ACTIVE";

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

    protected ParamVersion() {
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

    public String getStatus() {
        return status;
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
}
