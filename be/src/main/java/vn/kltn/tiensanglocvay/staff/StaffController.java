package vn.kltn.tiensanglocvay.staff;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.application.Dtos;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.RequireRole;
import vn.kltn.tiensanglocvay.security.Role;

/**
 * /staff/** (QT04–QT05, x-roles BRANCH_STAFF). Vai trò khác → 403 "Không có quyền." trước khi đọc body (như mock);
 * chưa đăng nhập → 401 "Chưa đăng nhập.".
 */
@RestController
@RequireRole(Role.BRANCH_STAFF)
public class StaffController {

    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping("/staff/applications")
    List<Dtos.ApplicationSummary> list(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
                                       @AuthenticationPrincipal CurrentUser user) {
        return staff.list(status, keyword, user);
    }

    @GetMapping("/staff/applications/by-qr/{token}")
    StaffDtos.PublicIdResult byQr(@PathVariable String token, @AuthenticationPrincipal CurrentUser user) {
        return staff.byQr(token, user);
    }

    @GetMapping("/staff/summary")
    StaffDtos.StaffSummary summary(@AuthenticationPrincipal CurrentUser user) {
        return staff.summary(user);
    }

    @PostMapping("/staff/applications/{id}/verification")
    Dtos.Application verify(@PathVariable String id, @RequestBody(required = false) StaffDtos.VerificationRequest req,
                            @AuthenticationPrincipal CurrentUser user) {
        return staff.verify(id, req, user);
    }

    @PostMapping("/staff/applications/{id}/decision")
    Dtos.Application decide(@PathVariable String id, @RequestBody(required = false) StaffDtos.DecisionRequest req,
                            @AuthenticationPrincipal CurrentUser user) {
        return staff.decide(id, req, user);
    }

    @PostMapping("/staff/applications/{id}/disbursement")
    Dtos.Application disburse(@PathVariable String id, @RequestBody(required = false) StaffDtos.DisbursementRequest req,
                              @AuthenticationPrincipal CurrentUser user) {
        return staff.disburse(id, req, user);
    }
}
