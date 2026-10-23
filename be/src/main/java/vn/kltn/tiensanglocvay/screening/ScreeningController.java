package vn.kltn.tiensanglocvay.screening;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.kltn.tiensanglocvay.engine.SimulationResponse;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/** Nhóm "Sàng lọc (QT01)" và POST /simulations của openapi.yaml. Quyền từng đường dẫn cấu hình ở SecurityConfig. */
@RestController
public class ScreeningController {

    private final ScreeningService screenings;
    private final SimulationService simulations;

    public ScreeningController(ScreeningService screenings, SimulationService simulations) {
        this.screenings = screenings;
        this.simulations = simulations;
    }

    /** Không @Valid: ScreeningService kiểm tra đồng ý và thu nhập trước (thứ tự của mock) rồi mới kiểm tra các trường còn lại. */
    @PostMapping("/screenings")
    @ResponseStatus(HttpStatus.CREATED)
    ScreeningResponse create(@RequestBody ScreeningRequest req, @AuthenticationPrincipal CurrentUser user) {
        return screenings.create(req, user);
    }

    /** {@code id} là mã công khai (UUID); nhận dạng chuỗi để mã sai định dạng trả 404 như không tìm thấy. */
    @GetMapping("/screenings/{id}")
    ScreeningResponse get(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        return screenings.get(id, user);
    }

    @PostMapping("/screenings/{id}/save")
    ScreeningResponse save(@PathVariable String id, @AuthenticationPrincipal CurrentUser user) {
        return screenings.save(id, user);
    }

    @GetMapping("/me/screenings")
    List<ScreeningResponse> mine(@AuthenticationPrincipal CurrentUser user) {
        return screenings.mine(user);
    }

    @PostMapping("/simulations")
    SimulationResponse simulate(@Valid @RequestBody SimulationRequest req, @AuthenticationPrincipal CurrentUser user) {
        return simulations.simulate(req, user);
    }
}
