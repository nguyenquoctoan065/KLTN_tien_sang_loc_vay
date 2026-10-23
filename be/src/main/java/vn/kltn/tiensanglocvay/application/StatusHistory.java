package vn.kltn.tiensanglocvay.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Bảng application_status_history (V6) – một dòng của Application.history. */
@Entity
@Table(name = "application_status_history")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private ApplicationStatus toStatus;

    @Column(name = "actor_type", nullable = false, updatable = false)
    private String actorType;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_name", nullable = false, updatable = false)
    private String actorName;

    @Column(updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected StatusHistory() {
    }

    StatusHistory(Long applicationId, ApplicationStatus fromStatus, ApplicationStatus toStatus, Actor actor, String note,
                  OffsetDateTime createdAt) {
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actor.type();
        this.actorId = actor.id();
        this.actorName = actor.name();
        this.note = note;
        this.createdAt = createdAt;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public String getActorName() {
        return actorName;
    }

    public String getNote() {
        return note;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
