package vn.kltn.tiensanglocvay.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bảng staff (NHÂN VIÊN): tài khoản nhân viên chi nhánh và quản trị viên. */
@Entity
@Table(name = "staff")
public class Staff {

    @Id
    private Integer id;

    @Column(name = "branch_id")
    private Integer branchId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status;

    protected Staff() {
    }

    public Integer getId() {
        return id;
    }

    public Integer getBranchId() {
        return branchId;
    }

    public String getFullName() {
        return fullName;
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

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
