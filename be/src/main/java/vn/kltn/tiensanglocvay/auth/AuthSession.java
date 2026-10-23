package vn.kltn.tiensanglocvay.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import vn.kltn.tiensanglocvay.security.AccountKind;

/** Bảng auth_session (V3): phiên đăng nhập, chỉ lưu SHA-256 của token. */
@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_kind", nullable = false)
    private AccountKind accountKind;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected AuthSession() {
    }

    public AuthSession(String tokenHash, AccountKind accountKind, long accountId, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.accountKind = accountKind;
        this.accountId = accountId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public AccountKind getAccountKind() {
        return accountKind;
    }

    public long getAccountId() {
        return accountId;
    }
}
