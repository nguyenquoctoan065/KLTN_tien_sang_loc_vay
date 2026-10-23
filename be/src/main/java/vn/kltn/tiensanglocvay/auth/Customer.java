package vn.kltn.tiensanglocvay.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Bảng customer (KHÁCH HÀNG). Mới ánh xạ các cột phục vụ tài khoản; các cột hồ sơ cá nhân thêm khi cần. */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phone;

    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Customer() {
    }

    public Customer(String phone, String email, String passwordHash, String fullName, OffsetDateTime createdAt) {
        this.phone = phone;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = "ACTIVE";
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
