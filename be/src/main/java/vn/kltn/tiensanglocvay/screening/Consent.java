package vn.kltn.tiensanglocvay.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng consent (ĐỒNG Ý XỬ LÝ DỮ LIỆU): phạm vi A khi sàng lọc, B khi lập hồ sơ (B4). */
@Entity
@Table(name = "consent")
public class Consent {

    public static final String SCOPE_A = "A";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private Long customerId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 1)
    private String scope;

    @Column(name = "doc_version", nullable = false)
    private String docVersion;

    @Column(name = "agreed_at", nullable = false)
    private OffsetDateTime agreedAt;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    protected Consent() {
    }

    public Consent(Long customerId, String scope, String docVersion, OffsetDateTime agreedAt) {
        this.customerId = customerId;
        this.scope = scope;
        this.docVersion = docVersion;
        this.agreedAt = agreedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getScope() {
        return scope;
    }

    public OffsetDateTime getAgreedAt() {
        return agreedAt;
    }

    public String getDocVersion() {
        return docVersion;
    }
}
