package vn.kltn.tiensanglocvay.application;

import static vn.kltn.tiensanglocvay.application.ApplicationStatus.APPROVED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.CANCELLED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.DISBURSED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.DRAFTING;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.EXPIRED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.NEED_MORE;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.REJECTED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.VERIFIED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.WAITING_VERIFY;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nơi duy nhất đổi trạng thái hồ sơ (docs/quy-tac-tinh-toan.md mục 12, mô tả schema Application trong openapi.yaml):
 * kiểm tra chuyển hợp lệ, cập nhật {@code updated_at}, ghi application_status_history. Người gọi kiểm tra điều kiện nghiệp vụ trước
 * và trả thông báo lỗi của mock; chuyển sai ở đây là lỗi lập trình ({@link IllegalStateException}).
 */
@Component
public class ApplicationStateMachine {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED = new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED.put(DRAFTING, EnumSet.of(WAITING_VERIFY, CANCELLED));
        ALLOWED.put(WAITING_VERIFY, EnumSet.of(VERIFIED, NEED_MORE, EXPIRED, CANCELLED));
        ALLOWED.put(NEED_MORE, EnumSet.of(WAITING_VERIFY, EXPIRED, CANCELLED));
        ALLOWED.put(VERIFIED, EnumSet.of(APPROVED, REJECTED, NEED_MORE));
        ALLOWED.put(APPROVED, EnumSet.of(DISBURSED));
        ALLOWED.put(EXPIRED, EnumSet.of(NEED_MORE, CANCELLED));
        ALLOWED.put(DISBURSED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(REJECTED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(ApplicationStatus.class));
    }

    private final StatusHistoryRepository history;
    private final Clock clock;

    ApplicationStateMachine(StatusHistoryRepository history, Clock clock) {
        this.history = history;
        this.clock = clock;
    }

    public static boolean allowed(ApplicationStatus from, ApplicationStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    /** Dòng lịch sử đầu tiên khi tạo hồ sơ (DRAFTING, mock: note "Tạo hồ sơ"). */
    @Transactional
    public void created(LoanApplication app, Actor actor) {
        history.save(new StatusHistory(app.getId(), null, app.getStatus(), actor, "Tạo hồ sơ", app.getCreatedAt()));
    }

    @Transactional
    public void transition(LoanApplication app, ApplicationStatus to, Actor actor, String note) {
        ApplicationStatus from = app.getStatus();
        if (!allowed(from, to)) {
            throw new IllegalStateException("Không chuyển được hồ sơ " + app.getCode() + " từ " + from + " sang " + to + ".");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        app.setStatus(to);
        app.touch(now);
        history.save(new StatusHistory(app.getId(), from, to, actor, note, now));
    }

    /** Dòng lịch sử giữ nguyên trạng thái (mock: chuyển chi nhánh đẩy {@code {status: a.status, note}}), cập nhật {@code updated_at}. */
    @Transactional
    public void note(LoanApplication app, Actor actor, String note) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        app.touch(now);
        history.save(new StatusHistory(app.getId(), app.getStatus(), app.getStatus(), actor, note, now));
    }
}
