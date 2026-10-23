package vn.kltn.tiensanglocvay.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bảng policy_param (THAM SỐ). {@code productId} rỗng nghĩa là tham số dùng chung. */
@Entity
@Table(name = "policy_param")
public class PolicyParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private Integer versionId;

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "param_key", nullable = false)
    private String key;

    @Column(name = "param_value", nullable = false)
    private String value;

    private String unit;

    protected PolicyParam() {
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

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }
}
