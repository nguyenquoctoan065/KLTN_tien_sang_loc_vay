package vn.kltn.tiensanglocvay.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.security.AccountKind;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/** Tương đương hàm {@code log(...)} trong fe/src/mocks/ctx.ts; {@code actor == null} nghĩa là hệ thống. */
@Service
public class ActivityLogService {

    private final ActivityLogRepository repo;
    private final Clock clock;

    public ActivityLogService(ActivityLogRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional
    public void log(CurrentUser actor, String action, String detail, Long applicationId) {
        String type = actor == null ? "SYSTEM" : actor.kind() == AccountKind.CUSTOMER ? "CUSTOMER" : "STAFF";
        repo.save(new ActivityLog(applicationId, type, actor == null ? null : actor.id(), action, detail, OffsetDateTime.now(clock)));
    }
}
