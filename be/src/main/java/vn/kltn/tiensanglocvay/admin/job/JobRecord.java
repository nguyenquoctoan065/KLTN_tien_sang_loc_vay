package vn.kltn.tiensanglocvay.admin.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Bảng scheduled_job (V9): tên tác vụ và lần chạy gần nhất. */
@Entity
@Table(name = "scheduled_job")
public class JobRecord {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "last_result")
    private String lastResult;

    protected JobRecord() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public OffsetDateTime getLastRunAt() {
        return lastRunAt;
    }

    public String getLastResult() {
        return lastResult;
    }

    public void ran(OffsetDateTime at, String result) {
        this.lastRunAt = at;
        this.lastResult = result.length() > 500 ? result.substring(0, 499) + "…" : result;
    }
}
