package vn.kltn.tiensanglocvay.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bảng loan_application (HỒ SƠ VAY) cùng các cột V6. Trạng thái chỉ đổi qua {@link ApplicationStateMachine}
 * (setter {@link #setStatus} chỉ để state machine dùng).
 */
@Entity
@Table(name = "loan_application")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã công khai (V5): API chỉ dùng mã này, không trả {@code id}. */
    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private Long planId;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Column(name = "consent_id", nullable = false, updatable = false)
    private Long consentId;

    @Column(name = "version_id")
    private Integer versionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "qr_token")
    private String qrToken;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "national_id")
    private String nationalId;

    private LocalDate dob;

    private String gender;

    private String address;

    private String email;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<CrossCheckWarning> warnings = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    private Recalculation recalc;

    protected LoanApplication() {
    }

    public LoanApplication(String code, Long customerId, Long planId, Integer branchId, Long consentId, Integer versionId,
                           LocalDate dob, String gender, OffsetDateTime now) {
        this.publicId = UUID.randomUUID();
        this.code = code;
        this.customerId = customerId;
        this.planId = planId;
        this.branchId = branchId;
        this.consentId = consentId;
        this.versionId = versionId;
        this.status = ApplicationStatus.DRAFTING;
        this.dob = dob;
        this.gender = gender;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getCode() {
        return code;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getPlanId() {
        return planId;
    }

    public Integer getBranchId() {
        return branchId;
    }

    /** Chỉ dùng khi quản trị ngừng hoạt động chi nhánh và chuyển hồ sơ sang chi nhánh khác (1.6). */
    public void setBranchId(Integer branchId) {
        this.branchId = branchId;
    }

    public Long getConsentId() {
        return consentId;
    }

    public Integer getVersionId() {
        return versionId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getContentHash() {
        return contentHash;
    }

    public OffsetDateTime getLockedAt() {
        return lockedAt;
    }

    public String getQrToken() {
        return qrToken;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    /** Khóa nội dung khi nộp (quy-tac-tinh-toan mục 11); giữ mã QR cũ nếu đã có. */
    public void lock(String contentHash, OffsetDateTime at, String qrToken, OffsetDateTime expiresAt, Integer versionId) {
        this.contentHash = contentHash;
        this.lockedAt = at;
        if (this.qrToken == null) {
            this.qrToken = qrToken;
        }
        this.expiresAt = expiresAt;
        this.versionId = versionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch(OffsetDateTime at) {
        this.updatedAt = at;
    }

    public String getFullName() {
        return fullName;
    }

    public String getNationalId() {
        return nationalId;
    }

    public LocalDate getDob() {
        return dob;
    }

    public String getGender() {
        return gender;
    }

    public String getAddress() {
        return address;
    }

    public String getEmail() {
        return email;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public List<CrossCheckWarning> getWarnings() {
        return warnings;
    }

    public Recalculation getRecalc() {
        return recalc;
    }

    public void setChecks(List<CrossCheckWarning> warnings, Recalculation recalc) {
        this.warnings = List.copyOf(warnings);
        this.recalc = recalc;
    }
}
