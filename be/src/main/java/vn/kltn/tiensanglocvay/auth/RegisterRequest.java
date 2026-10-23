package vn.kltn.tiensanglocvay.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Schema RegisterRequest. {@code otp} không ràng buộc ở đây: thiếu hoặc sai đều trả "Mã OTP không đúng." như mock.
 * {@code email} được cắt khoảng trắng rồi kiểm tra trong AuthService (mock cũng trim trước khi dùng).
 */
public record RegisterRequest(
        @NotNull(message = "Không được để trống.")
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại không hợp lệ.") String phone,
        String email,
        @NotNull(message = "Không được để trống.")
        @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự.") String password,
        String otp) {
}
