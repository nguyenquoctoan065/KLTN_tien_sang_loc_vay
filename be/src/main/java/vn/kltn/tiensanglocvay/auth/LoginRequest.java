package vn.kltn.tiensanglocvay.auth;

import jakarta.validation.constraints.NotNull;
import vn.kltn.tiensanglocvay.security.AccountKind;

/** Schema LoginRequest: khách hàng đăng nhập bằng số điện thoại hoặc email, nhân viên bằng email. */
public record LoginRequest(
        @NotNull(message = "Không được để trống.") AccountKind kind,
        @NotNull(message = "Không được để trống.") String username,
        @NotNull(message = "Không được để trống.") String password) {
}
