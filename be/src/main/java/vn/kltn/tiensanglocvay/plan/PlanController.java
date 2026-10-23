package vn.kltn.tiensanglocvay.plan;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.screening.SimulationRequest;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/** /plans/**, /me/plans (QT02). Quyền từng đường dẫn cấu hình ở SecurityConfig. */
@RestController
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    /** Không @Valid: mock kiểm tra đăng nhập trước khi xét dữ liệu, PlanService làm theo thứ tự đó. */
    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    LoanPlanDto create(@RequestBody SimulationRequest req, @AuthenticationPrincipal CurrentUser user) {
        return plans.create(req, user);
    }

    @GetMapping("/plans/{id}")
    LoanPlanDto get(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        return plans.get(id, user);
    }

    @PostMapping("/plans/{id}/confirm")
    LoanPlanDto confirm(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        return plans.confirm(id, user);
    }

    @GetMapping("/me/plans")
    List<LoanPlanDto> mine(@AuthenticationPrincipal CurrentUser user) {
        return plans.mine(user);
    }
}
