package vn.kltn.tiensanglocvay.admin.job;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.application.ApplicationStatus;
import vn.kltn.tiensanglocvay.application.LoanApplication;
import vn.kltn.tiensanglocvay.application.LoanApplicationRepository;
import vn.kltn.tiensanglocvay.notification.NotificationService;

/** REMIND_DRAFTS: hồ sơ DRAFTING không cập nhật quá 3 ngày → thông báo nhắc khách hàng (mỗi lần chạy nhắc lại, như mock). */
@Component
class RemindDraftsJob implements ScheduledJob {

    private final LoanApplicationRepository apps;
    private final NotificationService notifications;
    private final Clock clock;

    RemindDraftsJob(LoanApplicationRepository apps, NotificationService notifications, Clock clock) {
        this.apps = apps;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    public String code() {
        return "REMIND_DRAFTS";
    }

    @Override
    @Transactional
    public String run() {
        List<LoanApplication> old = apps.findStaleForUpdate(EnumSet.of(ApplicationStatus.DRAFTING), OffsetDateTime.now(clock).minusDays(3));
        for (LoanApplication a : old) {
            notifications.toCustomer(a.getCustomerId(), "Nhắc hoàn tất hồ sơ",
                    "Hồ sơ " + a.getCode() + " còn đang lập. Hoàn tất giấy tờ để nộp hồ sơ.", "/ho-so/" + a.getPublicId());
        }
        return "Gửi nhắc " + old.size() + " khách hàng.";
    }
}
