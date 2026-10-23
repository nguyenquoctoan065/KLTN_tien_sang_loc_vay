package vn.kltn.tiensanglocvay.auth;

/** Body của POST /auth/otp: có {@code phone} khi đăng ký; bỏ trống khi khách hàng đã đăng nhập. */
public record OtpRequest(String phone) {
}
