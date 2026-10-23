package vn.kltn.tiensanglocvay.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Bảng scoring_weight (TRỌNG SỐ): trọng số (%) của từng tiêu chí chấm điểm theo gói, trong một phiên bản. */
@Entity
@Table(name = "scoring_weight")
public class ScoringWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Integer versionId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(nullable = false)
    private String criterion;

    @Column(nullable = false)
    private BigDecimal weight;

    @Column(name = "adjust_factor")
    private BigDecimal adjustFactor;

    protected ScoringWeight() {
    }

    public Long getId() {
        return id;
    }

    public Integer getVersionId() {
        return versionId;
    }

    public Integer getProductId() {
        return productId;
    }

    public String getCriterion() {
        return criterion;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public BigDecimal getAdjustFactor() {
        return adjustFactor;
    }
}
