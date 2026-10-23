package vn.kltn.tiensanglocvay.admin;

import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.admin.AdminDtos.CreateStaffRequest;
import vn.kltn.tiensanglocvay.admin.AdminDtos.StaffAccount;
import vn.kltn.tiensanglocvay.admin.AdminDtos.UpdateStaffRequest;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.RequireRole;
import vn.kltn.tiensanglocvay.security.Role;

/** /admin/staff/** (1.5, x-roles ADMIN). */
@RestController
@RequireRole(Role.ADMIN)
public class AdminStaffController {

    private final AdminStaffService service;

    public AdminStaffController(AdminStaffService service) {
        this.service = service;
    }

    @GetMapping("/admin/staff")
    List<StaffAccount> list() {
        return service.list();
    }

    @PostMapping("/admin/staff")
    @ResponseStatus(HttpStatus.CREATED)
    StaffAccount create(@RequestBody(required = false) CreateStaffRequest b, @AuthenticationPrincipal CurrentUser user) {
        return service.create(b, user);
    }

    @PutMapping("/admin/staff/{id}")
    StaffAccount update(@PathVariable int id, @RequestBody(required = false) UpdateStaffRequest b, @AuthenticationPrincipal CurrentUser user) {
        return service.update(id, b, user);
    }

    /*
     * lock, unlock, reset-password: mock dùng chung một handler /admin/staff/:id/:action, ở đây tách 3 mapping để springdoc
     * liệt kê đúng 3 path như openapi.yaml (tests/contract/compare_endpoints.py). Action khác rơi vào mapping ẩn bên dưới
     * để trả đúng 404 “Thao tác không hợp lệ.” như mock.
     */
    @PostMapping("/admin/staff/{id}/lock")
    StaffAccount lock(@PathVariable int id, @AuthenticationPrincipal CurrentUser user) {
        return service.act(id, "lock", user);
    }

    @PostMapping("/admin/staff/{id}/unlock")
    StaffAccount unlock(@PathVariable int id, @AuthenticationPrincipal CurrentUser user) {
        return service.act(id, "unlock", user);
    }

    @PostMapping("/admin/staff/{id}/reset-password")
    StaffAccount resetPassword(@PathVariable int id, @AuthenticationPrincipal CurrentUser user) {
        return service.act(id, "reset-password", user);
    }

    @Hidden
    @PostMapping("/admin/staff/{id}/{action}")
    StaffAccount unknownAction(@PathVariable int id, @PathVariable String action, @AuthenticationPrincipal CurrentUser user) {
        return service.act(id, action, user);
    }
}
