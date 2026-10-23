package vn.kltn.tiensanglocvay.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;

/** Bảng plan_cross_sell (PHƯƠNG ÁN_BÁN CHÉO): sản phẩm bán chéo khách chọn kèm phương án. */
@Entity
@Table(name = "plan_cross_sell")
@IdClass(PlanCrossSell.Key.class)
public class PlanCrossSell {

    public record Key(Long planId, Integer crossSellId) implements Serializable {
    }

    @Id
    @Column(name = "plan_id")
    private Long planId;

    @Id
    @Column(name = "cross_sell_id")
    private Integer crossSellId;

    @Column(name = "extra_cost")
    private BigDecimal extraCost;

    private String benefit;

    protected PlanCrossSell() {
    }

    public PlanCrossSell(Long planId, Integer crossSellId, long extraCost, String benefit) {
        this.planId = planId;
        this.crossSellId = crossSellId;
        this.extraCost = BigDecimal.valueOf(extraCost);
        this.benefit = benefit;
    }

    public Integer getCrossSellId() {
        return crossSellId;
    }

    public BigDecimal getExtraCost() {
        return extraCost;
    }

    public String getBenefit() {
        return benefit;
    }
}
