package vn.kltn.tiensanglocvay.admin;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.admin.AdminDtos.CloneParamVersionRequest;
import vn.kltn.tiensanglocvay.admin.AdminDtos.IdResult;
import vn.kltn.tiensanglocvay.admin.AdminDtos.ParamVersionDetail;
import vn.kltn.tiensanglocvay.admin.AdminDtos.ParamVersionDto;
import vn.kltn.tiensanglocvay.admin.AdminDtos.TestRunResult;
import vn.kltn.tiensanglocvay.admin.AdminDtos.UpdateParamVersionRequest;
import vn.kltn.tiensanglocvay.common.OkResult;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.RequireRole;
import vn.kltn.tiensanglocvay.security.Role;

/** /admin/param-versions/** (QT07, x-roles ADMIN). Vai trò khác → 403 "Không có quyền." trước khi đọc body. */
@RestController
@RequireRole(Role.ADMIN)
public class ParamVersionController {

    private final ParamVersionService service;

    public ParamVersionController(ParamVersionService service) {
        this.service = service;
    }

    @GetMapping("/admin/param-versions")
    List<ParamVersionDto> list() {
        return service.list();
    }

    @GetMapping("/admin/param-versions/{id}")
    ParamVersionDetail get(@PathVariable int id) {
        return service.get(id);
    }

    @PostMapping("/admin/param-versions/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    IdResult cloneVersion(@PathVariable int id, @RequestBody(required = false) CloneParamVersionRequest b,
                          @AuthenticationPrincipal CurrentUser user) {
        return service.cloneVersion(id, b, user);
    }

    @PutMapping("/admin/param-versions/{id}")
    OkResult update(@PathVariable int id, @RequestBody(required = false) UpdateParamVersionRequest b,
                    @AuthenticationPrincipal CurrentUser user) {
        service.update(id, b, user);
        return OkResult.yes();
    }

    @DeleteMapping("/admin/param-versions/{id}")
    OkResult delete(@PathVariable int id, @AuthenticationPrincipal CurrentUser user) {
        service.delete(id, user);
        return OkResult.yes();
    }

    @PostMapping("/admin/param-versions/{id}/test")
    TestRunResult test(@PathVariable int id, @AuthenticationPrincipal CurrentUser user) {
        return service.test(id, user);
    }

    @PostMapping("/admin/param-versions/{id}/activate")
    ParamVersionDto activate(@PathVariable int id, @AuthenticationPrincipal CurrentUser user) {
        return service.activate(id, user);
    }
}
