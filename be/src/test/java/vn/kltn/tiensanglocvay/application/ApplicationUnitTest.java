package vn.kltn.tiensanglocvay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.APPROVED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.CANCELLED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.DISBURSED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.DRAFTING;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.EXPIRED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.NEED_MORE;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.REJECTED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.VERIFIED;
import static vn.kltn.tiensanglocvay.application.ApplicationStatus.WAITING_VERIFY;

import org.junit.jupiter.api.Test;

/** Bảng chuyển trạng thái (quy-tac-tinh-toan mục 12) và tiện ích chép từ mock. */
class ApplicationUnitTest {

    @Test
    void stateMachine_allowedTransitions() {
        assertThat(ApplicationStateMachine.allowed(DRAFTING, WAITING_VERIFY)).isTrue();
        assertThat(ApplicationStateMachine.allowed(DRAFTING, CANCELLED)).isTrue();
        assertThat(ApplicationStateMachine.allowed(DRAFTING, VERIFIED)).isFalse();
        assertThat(ApplicationStateMachine.allowed(WAITING_VERIFY, VERIFIED)).isTrue();
        assertThat(ApplicationStateMachine.allowed(WAITING_VERIFY, NEED_MORE)).isTrue();
        assertThat(ApplicationStateMachine.allowed(WAITING_VERIFY, EXPIRED)).isTrue();
        assertThat(ApplicationStateMachine.allowed(NEED_MORE, WAITING_VERIFY)).isTrue();
        assertThat(ApplicationStateMachine.allowed(EXPIRED, NEED_MORE)).isTrue();
        assertThat(ApplicationStateMachine.allowed(EXPIRED, WAITING_VERIFY)).isFalse();
        assertThat(ApplicationStateMachine.allowed(EXPIRED, CANCELLED)).isTrue();
        assertThat(ApplicationStateMachine.allowed(VERIFIED, APPROVED)).isTrue();
        assertThat(ApplicationStateMachine.allowed(VERIFIED, REJECTED)).isTrue();
        assertThat(ApplicationStateMachine.allowed(VERIFIED, NEED_MORE)).isTrue();
        assertThat(ApplicationStateMachine.allowed(VERIFIED, CANCELLED)).isFalse();
        assertThat(ApplicationStateMachine.allowed(APPROVED, DISBURSED)).isTrue();
        assertThat(ApplicationStateMachine.allowed(APPROVED, CANCELLED)).isFalse();
        for (ApplicationStatus to : ApplicationStatus.values()) {
            assertThat(ApplicationStateMachine.allowed(DISBURSED, to)).isFalse();
            assertThat(ApplicationStateMachine.allowed(REJECTED, to)).isFalse();
            assertThat(ApplicationStateMachine.allowed(CANCELLED, to)).isFalse();
        }
    }

    @Test
    void mockHelpers() {
        assertThat(ApplicationService.capitalizeWords("NGUYỄN  VĂN MINH")).isEqualTo("Nguyễn  Văn Minh");
        assertThat(ApplicationService.capitalizeWords("ĐỖ thị ánh")).isEqualTo("Đỗ thị ánh");
        assertThat(CrossCheckService.parseMoney("18.800.000 đ")).isEqualTo(18_800_000);
        assertThat(CrossCheckService.parseMoney("không rõ")).isZero();
        assertThat(CrossCheckService.parseMoney(null)).isZero();
        assertThat(CrossCheckService.reverse("15/05/1992", "/", "-")).isEqualTo("1992-05-15");
        assertThat(CrossCheckService.reverse("", "-", "/")).isEmpty();
        assertThat(ApplicationStatus.EXPIRED.label()).isEqualTo("Hết hạn");
    }
}
