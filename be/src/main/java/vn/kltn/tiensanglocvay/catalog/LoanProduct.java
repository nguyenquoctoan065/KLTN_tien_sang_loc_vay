package vn.kltn.tiensanglocvay.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bảng loan_product (GÓI VAY). */
@Entity
@Table(name = "loan_product")
public class LoanProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "repayment_method", nullable = false)
    private String repaymentMethod;

    private String description;

    @Column(nullable = false)
    private String status;

    protected LoanProduct() {
    }

    public Integer getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getProductType() {
        return productType;
    }

    public String getRepaymentMethod() {
        return repaymentMethod;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }
}
