package vn.kltn.tiensanglocvay.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Bảng otp_code (V3). */
@Entity
@Table(name = "otp_code")
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    protected OtpCode() {
    }

    public OtpCode(String target, String purpose, String codeHash, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.target = target;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void increaseAttempts() {
        attempts++;
    }
}
