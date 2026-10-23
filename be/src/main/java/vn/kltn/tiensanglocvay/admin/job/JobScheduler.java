package vn.kltn.tiensanglocvay.admin.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Chạy ba tác vụ theo cron {@code app.jobs.*} (giờ Việt Nam). Đặt cron "-" để tắt (profile test). Ghi nhật ký với tác nhân hệ thống;
 * lỗi chỉ ghi log ứng dụng, lần chạy sau thử lại.
 */
@Configuration
@EnableScheduling
class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);
    private static final String ZONE = "Asia/Ho_Chi_Minh";

    private final JobService jobs;

    JobScheduler(JobService jobs) {
        this.jobs = jobs;
    }

    @Scheduled(cron = "${app.jobs.expire-documents}", zone = ZONE)
    void expireDocuments() {
        run("EXPIRE_DOCUMENTS");
    }

    @Scheduled(cron = "${app.jobs.purge-retention}", zone = ZONE)
    void purgeRetention() {
        run("PURGE_RETENTION");
    }

    @Scheduled(cron = "${app.jobs.remind-drafts}", zone = ZONE)
    void remindDrafts() {
        run("REMIND_DRAFTS");
    }

    private void run(String code) {
        try {
            log.info("Tác vụ {}: {}", code, jobs.run(code, null).lastResult());
        } catch (RuntimeException e) {
            log.error("Tác vụ {} lỗi: {}", code, e.toString(), e);
        }
    }
}
