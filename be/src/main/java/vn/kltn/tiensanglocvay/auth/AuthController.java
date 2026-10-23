package vn.kltn.tiensanglocvay.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.common.OkResult;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.TokenAuthFilter;

/** Nhóm "Xác thực" trong openapi.yaml. Quyền truy cập từng đường dẫn cấu hình ở SecurityConfig. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/logout")
    OkResult logout(HttpServletRequest request, @AuthenticationPrincipal CurrentUser user) {
        auth.logout(TokenAuthFilter.extractToken(request), user);
        return OkResult.yes();
    }

    @PostMapping("/otp")
    OtpSent otp(@RequestBody(required = false) OtpRequest body, @AuthenticationPrincipal CurrentUser user) {
        return auth.sendOtp(body == null ? null : body.phone(), user);
    }

    @PostMapping("/register")
    LoginResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @GetMapping("/me")
    AuthUser me(@AuthenticationPrincipal CurrentUser user) {
        return auth.me(user);
    }

    @PostMapping("/change-password")
    OkResult changePassword(HttpServletRequest request, @AuthenticationPrincipal CurrentUser user,
                            @RequestBody ChangePasswordRequest req) {
        auth.changePassword(user, TokenAuthFilter.extractToken(request), req);
        return OkResult.yes();
    }
}
