package vn.kltn.tiensanglocvay.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Bảng verification (V1) – PHIẾU ĐỐI CHIẾU MB09, một phần tử của Application.verifications. */
@Entity
@Table(name = "verification")
public class Verification {

    public static final String MATCH = "MATCH";
    public static final String MISMATCH = "MISMATCH";
    public static final String MISSING = "MISSING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private Integer staffId;

    @Column(name = "overall_result", nullable = false, updatable = false)
    private String overallResult;

    @Column(updatable = false)
    private String note;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private OffsetDateTime verifiedAt;

    protected Verification() {
    }

    public Verification(Long applicationId, Integer staffId, String overallResult, String note, OffsetDateTime verifiedAt) {
        this.applicationId = applicationId;
        this.staffId = staffId;
        this.overallResult = overallResult;
        this.note = note;
        this.verifiedAt = verifiedAt;
    }

    public Long getId() {
        return id;
    }

    public Integer getStaffId() {
        return staffId;
    }

    public String getOverallResult() {
        return overallResult;
    }

    public String getNote() {
        return note;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }
}
