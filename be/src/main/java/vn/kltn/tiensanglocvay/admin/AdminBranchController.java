package vn.kltn.tiensanglocvay.admin;

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
import vn.kltn.tiensanglocvay.admin.AdminDtos.AdminBranch;
import vn.kltn.tiensanglocvay.admin.AdminDtos.BranchDeactivateResult;
import vn.kltn.tiensanglocvay.admin.AdminDtos.BranchInput;
import vn.kltn.tiensanglocvay.admin.AdminDtos.BranchView;
import vn.kltn.tiensanglocvay.admin.AdminDtos.DeactivateBranchRequest;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.RequireRole;
import vn.kltn.tiensanglocvay.security.Role;

/** /admin/branches/** (1.6, x-roles ADMIN). */
@RestController
@RequireRole(Role.ADMIN)
public class AdminBranchController {

    private final AdminBranchService service;

    public AdminBranchController(AdminBranchService service) {
        this.service = service;
    }

    @GetMapping("/admin/branches")
    List<AdminBranch> list() {
        return service.list();
    }

    @PostMapping("/admin/branches")
    @ResponseStatus(HttpStatus.CREATED)
    BranchView create(@RequestBody(required = false) BranchInput b, @AuthenticationPrincipal CurrentUser user) {
        return service.create(b, user);
    }

    @PutMapping("/admin/branches/{id}")
    BranchView update(@PathVariable int id, @RequestBody(required = false) BranchInput b, @AuthenticationPrincipal CurrentUser user) {
        return service.update(id, b, user);
    }

    @PostMapping("/admin/branches/{id}/deactivate")
    BranchDeactivateResult deactivate(@PathVariable int id, @RequestBody(required = false) DeactivateBranchRequest b,
                                      @AuthenticationPrincipal CurrentUser user) {
        return service.deactivate(id, b, user);
    }

    @PostMapping("/admin/branches/{id}/activate")
    BranchView activate(@PathVariable int id, @AuthenticationPrincipal CurrentUser user) {
        return service.activate(id, user);
    }
}
