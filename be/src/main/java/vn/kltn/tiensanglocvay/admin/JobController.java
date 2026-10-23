package vn.kltn.tiensanglocvay.admin;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.admin.AdminDtos.JobInfo;
import vn.kltn.tiensanglocvay.admin.job.JobService;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.RequireRole;
import vn.kltn.tiensanglocvay.security.Role;

/** 1.7 /admin/jobs (x-roles ADMIN). */
@RestController
@RequireRole(Role.ADMIN)
public class JobController {

    private final JobService jobs;

    public JobController(JobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/admin/jobs")
    List<JobInfo> list() {
        return jobs.list();
    }

    @PostMapping("/admin/jobs/{code}/run")
    JobInfo run(@PathVariable String code, @AuthenticationPrincipal CurrentUser user) {
        return jobs.run(code, user);
    }
}
