package vn.kltn.tiensanglocvay.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Bảng activity_log (NHẬT KÝ). */
@Entity
@Table(name = "activity_log")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false)
    private String action;

    private String detail;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ActivityLog() {
    }

    public ActivityLog(Long applicationId, String actorType, Long actorId, String action, String detail, OffsetDateTime createdAt) {
        this.applicationId = applicationId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public String getActorType() {
        return actorType;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
