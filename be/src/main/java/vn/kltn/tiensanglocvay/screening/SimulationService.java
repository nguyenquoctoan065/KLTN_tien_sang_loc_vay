package vn.kltn.tiensanglocvay.screening;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.catalog.CrossSellProductRepository;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.engine.CrossSell;
import vn.kltn.tiensanglocvay.engine.Engine;
import vn.kltn.tiensanglocvay.engine.EngineInput;
import vn.kltn.tiensanglocvay.engine.ParamSet;
import vn.kltn.tiensanglocvay.engine.SimulationResponse;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.policy.EngineParams;
import vn.kltn.tiensanglocvay.policy.PolicySnapshot;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/** 3.1: mô phỏng MB04 theo phiên bản tham số đang áp dụng (như mock), không lưu dữ liệu. */
@Service
public class SimulationService {

    private final ScreeningService screenings;
    private final CrossSellProductRepository crossSells;
    private final ActivePolicy policy;
    private final Clock clock;

    public SimulationService(ScreeningService screenings, CrossSellProductRepository crossSells, ActivePolicy policy, Clock clock) {
        this.screenings = screenings;
        this.crossSells = crossSells;
        this.policy = policy;
        this.clock = clock;
    }

    /** Lượt đã lưu vào tài khoản chỉ chủ tài khoản mô phỏng được (404 với người khác). */
    @Transactional(readOnly = true)
    public SimulationResponse simulate(SimulationRequest req, CurrentUser user) {
        return run(screenings.findFor(req.screeningId(), user), req);
    }

    /** 422 nếu lượt sàng lọc không đạt điều kiện loại trừ của gói; dùng chung cho POST /plans. */
    public SimulationResponse run(Screening s, SimulationRequest req) {
        PolicySnapshot p = policy.current();
        ParamSet ps = EngineParams.paramSet(p);
        EngineInput inp = screenings.engineInput(s);
        LocalDate today = LocalDate.now(clock);
        if (!Engine.exclusion(inp, ps, req.productCode(), today).eligible()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Hồ sơ không đạt điều kiện của gói vay này.");
        }
        return Engine.simulate(inp, ps, EngineParams.weightSet(p), req.toEngine(), allCrossSells(), today);
    }

    /** Toàn bộ sản phẩm bán chéo theo id, cả bản ngừng áp dụng (mock truyền toàn bộ db.crossSells). */
    public List<CrossSell> allCrossSells() {
        return crossSells.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getId(), b.getId()))
                .map(c -> new CrossSell(c.getId(), c.getName(), c.getEstCost() == null ? 0 : c.getEstCost().longValueExact(),
                        c.getScoreBonus() == null ? 0 : c.getScoreBonus().doubleValue(),
                        c.getRateDiscount() == null ? 0 : c.getRateDiscount().doubleValue(), c.getStatus()))
                .toList();
    }
}
