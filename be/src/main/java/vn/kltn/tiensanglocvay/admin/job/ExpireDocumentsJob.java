package vn.kltn.tiensanglocvay.admin.job;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.application.Actor;
import vn.kltn.tiensanglocvay.application.ApplicationStateMachine;
import vn.kltn.tiensanglocvay.application.ApplicationStatus;
import vn.kltn.tiensanglocvay.application.LoanApplication;
import vn.kltn.tiensanglocvay.application.LoanApplicationRepository;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.notification.NotificationService;

/** EXPIRE_DOCUMENTS: hồ sơ WAITING_VERIFY / NEED_MORE có {@code expires_at} đã qua → EXPIRED, báo khách hàng (tác nhân hệ thống). */
@Component
class ExpireDocumentsJob implements ScheduledJob {

    private final LoanApplicationRepository apps;
    private final ApplicationStateMachine states;
    private final NotificationService notifications;
    private final ActivityLogService activity;
    private final Clock clock;

    ExpireDocumentsJob(LoanApplicationRepository apps, ApplicationStateMachine states, NotificationService notifications,
                       ActivityLogService activity, Clock clock) {
        this.apps = apps;
        this.states = states;
        this.notifications = notifications;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    public String code() {
        return "EXPIRE_DOCUMENTS";
    }

    @Override
    @Transactional
    public String run() {
        List<LoanApplication> hit = apps.findExpiredForUpdate(EnumSet.of(ApplicationStatus.WAITING_VERIFY, ApplicationStatus.NEED_MORE),
                OffsetDateTime.now(clock));
        for (LoanApplication a : hit) {
            states.transition(a, ApplicationStatus.EXPIRED, Actor.SYSTEM, "Giấy tờ quá thời hạn hiệu lực");
            notifications.toCustomer(a.getCustomerId(), "Hồ sơ hết hạn",
                    "Giấy tờ của hồ sơ " + a.getCode() + " đã quá thời hạn hiệu lực. Cập nhật giấy tờ để nộp lại.", "/ho-so/" + a.getPublicId());
            activity.log(null, "Chuyển hết hạn", "Giấy tờ quá thời hạn hiệu lực", a.getId());
        }
        return hit.size() + " hồ sơ chuyển “Hết hạn”.";
    }
}
