package vn.kltn.tiensanglocvay.admin.job;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.kltn.tiensanglocvay.application.ApplicationDataEraser;
import vn.kltn.tiensanglocvay.application.ApplicationStatus;
import vn.kltn.tiensanglocvay.application.LoanApplicationRepository;
import vn.kltn.tiensanglocvay.engine.JsFormat;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;

/**
 * PURGE_RETENTION: hồ sơ REJECTED / CANCELLED / EXPIRED cập nhật lần cuối quá {@code RETENTION_MONTHS} tháng (mặc định 12, mỗi tháng
 * 30 ngày như mock) bị xóa tệp giấy tờ (Cloudinary / kho cục bộ), trường trích xuất, giấy đề nghị, số CCCD, địa chỉ.
 * Mỗi hồ sơ một giao dịch; tệp xóa trong giao dịch đó, xóa lỗi thì hồ sơ giữ nguyên để lần chạy sau thử lại.
 */
@Component
class PurgeRetentionJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(PurgeRetentionJob.class);
    private static final Set<ApplicationStatus> CLOSED = EnumSet.of(ApplicationStatus.REJECTED, ApplicationStatus.CANCELLED,
            ApplicationStatus.EXPIRED);

    private final LoanApplicationRepository apps;
    private final ApplicationDataEraser eraser;
    private final ActivePolicy policy;
    private final TransactionTemplate tx;
    private final Clock clock;

    PurgeRetentionJob(LoanApplicationRepository apps, ApplicationDataEraser eraser, ActivePolicy policy, TransactionTemplate tx,
                      Clock clock) {
        this.apps = apps;
        this.eraser = eraser;
        this.policy = policy;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public String code() {
        return "PURGE_RETENTION";
    }

    @Override
    public String run() {
        double months = retentionMonths();
        OffsetDateTime limit = OffsetDateTime.now(clock).minusSeconds(Math.round(months * 30 * 86_400));
        int documents = 0;
        int failed = 0;
        for (Long id : apps.findStaleIds(CLOSED, limit)) {
            try {
                Integer n = tx.execute(status -> apps.findByIdForUpdate(id)
                        .filter(a -> CLOSED.contains(a.getStatus()) && a.getUpdatedAt().isBefore(limit))
                        .map(a -> {
                            ApplicationDataEraser.Erased erased = eraser.erase(a);
                            eraser.deleteFiles(erased.files());
                            return erased.documents();
                        }).orElse(0));
                documents += n == null ? 0 : n;
            } catch (RuntimeException e) {
                failed++;
                log.error("PURGE_RETENTION: không xóa được dữ liệu hồ sơ #{}: {}", id, e.toString());
            }
        }
        return "Xóa " + documents + " tài liệu của hồ sơ đã đóng quá " + JsFormat.num(months) + " tháng."
                + (failed == 0 ? "" : " " + failed + " hồ sơ chưa xóa được tệp, sẽ thử lại lần sau.");
    }

    /** {@code paramValue('RETENTION_MONTHS') || 12} của mock: thiếu, sai hoặc bằng 0 thì 12. */
    private double retentionMonths() {
        String raw = policy.current().common().get("RETENTION_MONTHS");
        try {
            double v = raw == null ? 0 : new BigDecimal(raw.trim()).doubleValue();
            return v > 0 ? v : 12;
        } catch (NumberFormatException e) {
            return 12;
        }
    }
}
