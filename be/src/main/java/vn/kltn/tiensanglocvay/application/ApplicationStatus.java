package vn.kltn.tiensanglocvay.application;

import java.util.EnumSet;
import java.util.Set;

/** Trạng thái hồ sơ (schema ApplicationStatus); nhãn tiếng Việt giống {@code STATUS_LABEL} trong fe/src/mocks/ctx.ts. */
public enum ApplicationStatus {
    DRAFTING("Đang lập hồ sơ"),
    WAITING_VERIFY("Chờ đối chiếu"),
    NEED_MORE("Cần bổ sung"),
    VERIFIED("Đã đối chiếu"),
    APPROVED("Được phê duyệt"),
    DISBURSED("Đã giải ngân"),
    REJECTED("Bị từ chối"),
    CANCELLED("Đã hủy"),
    EXPIRED("Hết hạn");

    /** Hồ sơ đang mở (mock {@code OPEN_STATUSES}). */
    public static final Set<ApplicationStatus> OPEN = EnumSet.of(DRAFTING, WAITING_VERIFY, NEED_MORE);
    /** Khách hàng sửa được nội dung (mock {@code editable()}). */
    public static final Set<ApplicationStatus> EDITABLE = EnumSet.of(DRAFTING, NEED_MORE, EXPIRED);
    /** Khách hàng hủy trực tuyến được (mock {@code OPEN_STATUSES.concat(['EXPIRED'])}). */
    public static final Set<ApplicationStatus> CANCELLABLE = EnumSet.of(DRAFTING, WAITING_VERIFY, NEED_MORE, EXPIRED);

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
