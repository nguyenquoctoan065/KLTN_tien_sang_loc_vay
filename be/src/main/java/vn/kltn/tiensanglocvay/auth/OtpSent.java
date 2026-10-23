package vn.kltn.tiensanglocvay.auth;

/** Schema OtpSent; {@code devOtp} chỉ có khi cấu hình mã cố định (profile dev). */
public record OtpSent(boolean sent, String devOtp) {
}
