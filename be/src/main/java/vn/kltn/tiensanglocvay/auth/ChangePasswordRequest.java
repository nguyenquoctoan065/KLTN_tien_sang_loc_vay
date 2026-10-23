package vn.kltn.tiensanglocvay.auth;

/** Schema ChangePasswordRequest. Kiểm tra trong AuthService để thông báo giống mock. */
public record ChangePasswordRequest(String oldPassword, String newPassword) {
}
