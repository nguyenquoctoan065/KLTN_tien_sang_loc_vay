package vn.kltn.tiensanglocvay.privacy;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * /me/data, /me/consents/withdraw (QT06). Chưa đăng nhập hoặc không phải khách hàng → 401 "Cần đăng nhập tài khoản khách hàng."
 * do CustomerOnlyFilter trả trước khi đọc body (như mock).
 */
@RestController
public class PrivacyController {

    private final PrivacyService privacy;

    public PrivacyController(PrivacyService privacy) {
        this.privacy = privacy;
    }

    @GetMapping("/me/data")
    PrivacyDtos.MyData data(@AuthenticationPrincipal CurrentUser user) {
        return privacy.myData(user);
    }

    @PostMapping("/me/consents/withdraw")
    PrivacyDtos.WithdrawConsentResult withdraw(@RequestBody(required = false) PrivacyDtos.WithdrawConsentRequest req,
                                               @AuthenticationPrincipal CurrentUser user) {
        return privacy.withdraw(req, user);
    }
}
