package vn.kltn.tiensanglocvay.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Bảng approval_result (V1) – KẾT QUẢ XÉT DUYỆT MB16 kèm giải ngân. Mỗi lần ghi kết quả thêm một dòng; Application.approval là dòng
 * mới nhất (mock ghi đè {@code app.approval}).
 */
@Entity
@Table(name = "approval_result")
public class ApprovalResult {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String MORE_INFO = "MORE_INFO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private Integer staffId;

    @Column(name = "result_type", nullable = false, updatable = false)
    private String resultType;

    @Column(name = "approved_amount", updatable = false)
    private BigDecimal approvedAmount;

    @Column(name = "approved_term", updatable = false)
    private Integer approvedTerm;

    @Column(name = "approved_rate", updatable = false)
    private BigDecimal approvedRate;

    @Column(updatable = false)
    private String reason;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "disbursed_at")
    private LocalDate disbursedAt;

    @Column(name = "disbursed_amount")
    private BigDecimal disbursedAmount;

    private String beneficiary;

    protected ApprovalResult() {
    }

    public ApprovalResult(Long applicationId, Integer staffId, String resultType, Long approvedAmount, Integer approvedTerm,
                          BigDecimal approvedRate, String reason, OffsetDateTime recordedAt) {
        this.applicationId = applicationId;
        this.staffId = staffId;
        this.resultType = resultType;
        this.approvedAmount = approvedAmount == null ? null : BigDecimal.valueOf(approvedAmount);
        this.approvedTerm = approvedTerm;
        this.approvedRate = approvedRate;
        this.reason = reason;
        this.recordedAt = recordedAt;
    }

    public void disburse(LocalDate at, long amount, String beneficiary) {
        this.disbursedAt = at;
        this.disbursedAmount = BigDecimal.valueOf(amount);
        this.beneficiary = beneficiary;
    }

    public Long getId() {
        return id;
    }

    public Integer getStaffId() {
        return staffId;
    }

    public String getResultType() {
        return resultType;
    }

    public Long getApprovedAmount() {
        return approvedAmount == null ? null : approvedAmount.longValueExact();
    }

    public Integer getApprovedTerm() {
        return approvedTerm;
    }

    public Double getApprovedRate() {
        return approvedRate == null ? null : approvedRate.doubleValue();
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public LocalDate getDisbursedAt() {
        return disbursedAt;
    }

    public Long getDisbursedAmount() {
        return disbursedAmount == null ? null : disbursedAmount.longValueExact();
    }

    public String getBeneficiary() {
        return beneficiary;
    }
}
