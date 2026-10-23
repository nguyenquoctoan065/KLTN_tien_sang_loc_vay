package vn.kltn.tiensanglocvay.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng loan_plan (PHƯƠNG ÁN VAY, MB07). Bảng không có customer_id: chủ phương án là khách hàng của lượt sàng lọc
 * ({@code screening.customer_id}), được gắn khi lưu phương án và không đổi về sau.
 */
@Entity
@Table(name = "loan_plan")
public class LoanPlan {

    public static final String DRAFT = "DRAFT";
    public static final String CONFIRMED = "CONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã công khai (V5): API chỉ dùng mã này, không trả {@code id}. */
    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "screening_id", nullable = false)
    private Long screeningId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "ref_rate", nullable = false)
    private BigDecimal refRate;

    @Column(name = "monthly_payment", nullable = false)
    private BigDecimal monthlyPayment;

    @Column(name = "total_interest", nullable = false)
    private BigDecimal totalInterest;

    @Column(name = "dti_after", nullable = false)
    private BigDecimal dtiAfter;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LoanPlan() {
    }

    public LoanPlan(Long screeningId, Integer productId, long amount, int termMonths, double refRate, long monthlyPayment,
                    long totalInterest, double dtiAfter, double score, OffsetDateTime createdAt) {
        this.publicId = UUID.randomUUID();
        this.screeningId = screeningId;
        this.productId = productId;
        this.amount = BigDecimal.valueOf(amount);
        this.termMonths = termMonths;
        this.refRate = BigDecimal.valueOf(refRate);
        this.monthlyPayment = BigDecimal.valueOf(monthlyPayment);
        this.totalInterest = BigDecimal.valueOf(totalInterest);
        this.dtiAfter = BigDecimal.valueOf(dtiAfter);
        this.score = BigDecimal.valueOf(score);
        this.status = DRAFT;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getScreeningId() {
        return screeningId;
    }

    public Integer getProductId() {
        return productId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public BigDecimal getRefRate() {
        return refRate;
    }

    public BigDecimal getMonthlyPayment() {
        return monthlyPayment;
    }

    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    public BigDecimal getDtiAfter() {
        return dtiAfter;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getStatus() {
        return status;
    }

    public void confirm() {
        this.status = CONFIRMED;
    }

    /** PUT /applications/{id}/plan: điều chỉnh phương án ngay trong hồ sơ (một phương án chỉ có một hồ sơ chưa hủy). */
    public void adjust(long amount, int termMonths, double refRate, long monthlyPayment, long totalInterest, double dtiAfter,
                       double score) {
        this.amount = BigDecimal.valueOf(amount);
        this.termMonths = termMonths;
        this.refRate = BigDecimal.valueOf(refRate);
        this.monthlyPayment = BigDecimal.valueOf(monthlyPayment);
        this.totalInterest = BigDecimal.valueOf(totalInterest);
        this.dtiAfter = BigDecimal.valueOf(dtiAfter);
        this.score = BigDecimal.valueOf(score);
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
