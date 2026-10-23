package vn.kltn.tiensanglocvay.application;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * /me/applications, /applications/** (QT03). POST /applications được CustomerOnlyFilter chặn trước (401 "Cần đăng nhập tài khoản
 * khách hàng."); các đường dẫn khác cần đăng nhập, người không phải chủ hồ sơ nhận 404 như mock.
 */
@RestController
public class ApplicationController {

    private final ApplicationService applications;
    private final DocumentUploadService uploads;

    public ApplicationController(ApplicationService applications, DocumentUploadService uploads) {
        this.applications = applications;
        this.uploads = uploads;
    }

    @GetMapping("/me/applications")
    List<Dtos.ApplicationSummary> mine(@AuthenticationPrincipal CurrentUser user) {
        return applications.mine(user);
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    Dtos.Application create(@RequestBody Dtos.CreateApplicationRequest req, @AuthenticationPrincipal CurrentUser user) {
        return applications.create(req, user);
    }

    @GetMapping("/applications/{id}")
    Dtos.Application get(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        return applications.get(id, user);
    }

    @PutMapping("/applications/{id}/personal")
    Dtos.Application personal(@PathVariable String id, @RequestBody Dtos.PersonalInfo req, @AuthenticationPrincipal CurrentUser user) {
        return applications.updatePersonal(id, req, user);
    }

    @PostMapping("/applications/{id}/documents/{docId}")
    Dtos.Application upload(@PathVariable String id, @PathVariable long docId, @RequestPart(name = "file", required = false) MultipartFile file,
                            @RequestParam(name = "issuedDate", required = false) String issuedDate,
                            @AuthenticationPrincipal CurrentUser user) {
        return uploads.upload(id, docId, file, issuedDate, user);
    }

    @PutMapping("/applications/{id}/documents/{docId}/fields")
    Dtos.Application fields(@PathVariable String id, @PathVariable long docId, @RequestBody Dtos.ConfirmFieldsRequest req,
                            @AuthenticationPrincipal CurrentUser user) {
        return applications.confirmFields(id, docId, req, user);
    }

    @PutMapping("/applications/{id}/plan")
    Dtos.Application plan(@PathVariable String id, @RequestBody Dtos.UpdatePlanRequest req, @AuthenticationPrincipal CurrentUser user) {
        return applications.updatePlan(id, req, user);
    }

    @PostMapping("/applications/{id}/request-form")
    Dtos.Application createForm(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        return applications.createRequestForm(id, user);
    }

    @PutMapping("/applications/{id}/request-form")
    Dtos.Application updateForm(@PathVariable String id, @RequestBody Dtos.UpdateRequestFormRequest req,
                                @AuthenticationPrincipal CurrentUser user) {
        return applications.updateRequestForm(id, req, user);
    }

    @PostMapping("/applications/{id}/submit")
    Dtos.Application submit(@PathVariable String id, @RequestBody(required = false) Dtos.OtpBody req,
                            @AuthenticationPrincipal CurrentUser user) {
        return applications.submit(id, req, user);
    }

    @PostMapping("/applications/{id}/cancel")
    Dtos.Application cancel(@PathVariable String id, @RequestBody(required = false) Dtos.CancelRequest req,
                            @AuthenticationPrincipal CurrentUser user) {
        return applications.cancel(id, req, user);
    }
}
