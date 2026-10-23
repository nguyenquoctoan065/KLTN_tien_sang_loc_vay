package vn.kltn.tiensanglocvay.notification;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.common.OkResult;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/** 5.4 Thông báo trong ứng dụng; cần đăng nhập (SecurityConfig). */
@RestController
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    List<AppNotification> list(@AuthenticationPrincipal CurrentUser user) {
        return notifications.list(user);
    }

    @PostMapping("/notifications/{id}/read")
    OkResult read(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
        notifications.read(id, user);
        return OkResult.yes();
    }

    @PostMapping("/notifications/read-all")
    OkResult readAll(@AuthenticationPrincipal CurrentUser user) {
        notifications.readAll(user);
        return OkResult.yes();
    }
}
