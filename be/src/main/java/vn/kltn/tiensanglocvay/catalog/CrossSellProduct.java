package vn.kltn.tiensanglocvay.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Bảng cross_sell_product (SẢN PHẨM BÁN CHÉO). */
@Entity
@Table(name = "cross_sell_product")
public class CrossSellProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "est_cost")
    private BigDecimal estCost;

    @Column(name = "score_bonus")
    private BigDecimal scoreBonus;

    @Column(name = "rate_discount")
    private BigDecimal rateDiscount;

    @Column(nullable = false)
    private String status;

    protected CrossSellProduct() {
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getProductType() {
        return productType;
    }

    public BigDecimal getEstCost() {
        return estCost;
    }

    public BigDecimal getScoreBonus() {
        return scoreBonus;
    }

    public BigDecimal getRateDiscount() {
        return rateDiscount;
    }

    public String getStatus() {
        return status;
    }
}
