package vn.kltn.tiensanglocvay.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Bảng screening (LƯỢT SÀNG LỌC): thông tin khai MB01 không định danh. {@code customerId} có khi đã lưu vào tài khoản. */
@Entity
@Table(name = "screening")
public class Screening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã công khai (V5): API chỉ dùng mã này, không trả {@code id}. */
    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "version_id", nullable = false)
    private Integer versionId;

    @Column(name = "consent_id", nullable = false)
    private Long consentId;

    @Column(nullable = false)
    private String nationality;

    @Column(nullable = false)
    private LocalDate dob;

    @Column(nullable = false)
    private String gender;

    @Column(name = "marital_status", nullable = false)
    private String maritalStatus;

    @Column(nullable = false)
    private int dependents;

    @Column(name = "avg_income", nullable = false)
    private BigDecimal avgIncome;

    @Column(name = "salary_bank", nullable = false)
    private String salaryBank;

    @Column(name = "is_abbank_staff", nullable = false)
    private boolean abbankStaff;

    @Column(name = "credit_history", nullable = false)
    private String creditHistory;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "desired_amount", nullable = false)
    private BigDecimal desiredAmount;

    @Column(name = "desired_term", nullable = false)
    private int desiredTerm;

    @Column(name = "disposable_income")
    private BigDecimal disposableIncome;

    @Column
    private BigDecimal dti;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Screening() {
    }

    public Screening(Long customerId, Integer versionId, Long consentId, ScreeningRequest r, long disposableIncome, BigDecimal dti,
                     OffsetDateTime createdAt) {
        this.publicId = UUID.randomUUID();
        this.customerId = customerId;
        this.versionId = versionId;
        this.consentId = consentId;
        this.nationality = r.nationality();
        this.dob = r.dob();
        this.gender = r.gender().name();
        this.maritalStatus = r.maritalStatus().name();
        this.dependents = r.dependents();
        this.avgIncome = BigDecimal.valueOf(r.avgIncome());
        this.salaryBank = r.salaryBank();
        this.abbankStaff = r.isAbbankStaff();
        this.creditHistory = r.creditHistory().name();
        this.purpose = r.purpose();
        this.desiredAmount = BigDecimal.valueOf(r.desiredAmount());
        this.desiredTerm = r.desiredTerm();
        this.disposableIncome = BigDecimal.valueOf(disposableIncome);
        this.dti = dti;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Integer getVersionId() {
        return versionId;
    }

    public Long getConsentId() {
        return consentId;
    }

    public String getNationality() {
        return nationality;
    }

    public LocalDate getDob() {
        return dob;
    }

    public String getGender() {
        return gender;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public int getDependents() {
        return dependents;
    }

    public BigDecimal getAvgIncome() {
        return avgIncome;
    }

    public String getSalaryBank() {
        return salaryBank;
    }

    public boolean isAbbankStaff() {
        return abbankStaff;
    }

    public String getCreditHistory() {
        return creditHistory;
    }

    public String getPurpose() {
        return purpose;
    }

    public BigDecimal getDesiredAmount() {
        return desiredAmount;
    }

    public int getDesiredTerm() {
        return desiredTerm;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
