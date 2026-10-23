package vn.kltn.tiensanglocvay.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng loan_request_form (GIẤY ĐỀ NGHỊ VAY VỐN, MB15). Bản nháp bị xóa mỗi khi nội dung hồ sơ thay đổi (như mock). */
@Entity
@Table(name = "loan_request_form")
public class LoanRequestForm {

    public static final String DRAFT = "DRAFT";
    public static final String CONFIRMED = "CONFIRMED";

    /** Một ô của giấy đề nghị (phần tử {@code content} của schema LoanRequestForm). */
    public record Item(String label, String key, String value, boolean editable) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Item> content;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "otp_confirmed_at")
    private OffsetDateTime otpConfirmedAt;

    protected LoanRequestForm() {
    }

    public LoanRequestForm(Long applicationId, List<Item> content, OffsetDateTime createdAt) {
        this.applicationId = applicationId;
        this.content = List.copyOf(content);
        this.status = DRAFT;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public List<Item> getContent() {
        return content;
    }

    void setContent(List<Item> content) {
        this.content = List.copyOf(content);
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getOtpConfirmedAt() {
        return otpConfirmedAt;
    }

    void confirmByOtp(OffsetDateTime at) {
        this.status = CONFIRMED;
        this.otpConfirmedAt = at;
    }
}
