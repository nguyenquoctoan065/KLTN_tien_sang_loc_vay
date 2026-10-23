package vn.kltn.tiensanglocvay.plan;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.catalog.CatalogService;
import vn.kltn.tiensanglocvay.catalog.LoanProduct;
import vn.kltn.tiensanglocvay.catalog.LoanProductRepository;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.common.PublicIds;
import vn.kltn.tiensanglocvay.engine.CrossSell;
import vn.kltn.tiensanglocvay.engine.Engine;
import vn.kltn.tiensanglocvay.engine.JsFormat;
import vn.kltn.tiensanglocvay.engine.ProductCode;
import vn.kltn.tiensanglocvay.engine.SimulationResponse;
import vn.kltn.tiensanglocvay.screening.Screening;
import vn.kltn.tiensanglocvay.screening.ScreeningService;
import vn.kltn.tiensanglocvay.screening.SimulationRequest;
import vn.kltn.tiensanglocvay.screening.SimulationService;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/** 3.2: lưu, xem, xác nhận phương án vay. Thông báo lỗi lấy đúng chữ trong fe/src/mocks/handlers/screening.ts. */
@Service
public class PlanService {

    static final String MSG_NOT_FOUND = "Không tìm thấy phương án.";

    private final LoanPlanRepository plans;
    private final PlanCrossSellRepository planCrossSells;
    private final LoanProductRepository products;
    private final ScreeningService screenings;
    private final SimulationService simulations;
    private final CatalogService catalog;
    private final ActivityLogService activity;
    private final Clock clock;

    public PlanService(LoanPlanRepository plans, PlanCrossSellRepository planCrossSells, LoanProductRepository products,
                       ScreeningService screenings, SimulationService simulations, CatalogService catalog, ActivityLogService activity,
                       Clock clock) {
        this.plans = plans;
        this.planCrossSells = planCrossSells;
        this.products = products;
        this.screenings = screenings;
        this.simulations = simulations;
        this.catalog = catalog;
        this.activity = activity;
        this.clock = clock;
    }

    /** Tính lại bằng tham số đang áp dụng; 422 nếu không đạt điều kiện gói hoặc vượt ngưỡng. */
    @Transactional
    public LoanPlanDto create(SimulationRequest req, CurrentUser user) {
        if (!ScreeningService.isCustomer(user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập tài khoản khách hàng để lưu phương án.");
        }
        screenings.validate(req);
        Screening s = screenings.findFor(req.screeningId(), user);
        SimulationResponse sim = simulations.run(s, req);
        if (!sim.ok()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Phương án chưa đạt: " + String.join(" ", sim.issues()));
        }
        screenings.claim(s, user.id());
        LoanPlan plan = plans.save(new LoanPlan(s.getId(), productId(sim.productCode()), sim.amount(), sim.termMonths(), sim.rate(),
                sim.firstPayment(), sim.totalInterest(), sim.dtiAfter(), sim.score(),
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)));
        for (CrossSell c : simulations.allCrossSells()) {
            if (req.crossSellIds().contains(c.id())) {
                planCrossSells.save(new PlanCrossSell(plan.getId(), c.id(), c.estCost(),
                        "Giảm " + JsFormat.num(c.rateDiscount()) + "%/năm lãi suất tham chiếu, +" + JsFormat.num(c.scoreBonus()) + " điểm ưu tiên"));
            }
        }
        activity.log(user, "Lưu phương án vay", "Phương án #" + PublicIds.shortCode(plan.getPublicId()), null);
        return toDto(plan);
    }

    @Transactional(readOnly = true)
    public LoanPlanDto get(String id, CurrentUser user) {
        return toDto(owned(id, user));
    }

    @Transactional
    public LoanPlanDto confirm(String id, CurrentUser user) {
        LoanPlan p = owned(id, user);
        p.confirm();
        activity.log(user, "Xác nhận phương án vay", "Phương án #" + PublicIds.shortCode(p.getPublicId()), null);
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public List<LoanPlanDto> mine(CurrentUser user) {
        if (!ScreeningService.isCustomer(user)) {
            return List.of();
        }
        return plans.findByCustomer(user.id()).stream().map(this::toDto).toList();
    }

    /**
     * Phương án của chính khách hàng theo mã công khai; mã sai định dạng, không đăng nhập, khác khách hoặc không tồn tại
     * đều 404 như mock.
     */
    private LoanPlan owned(String id, CurrentUser user) {
        UUID publicId = PublicIds.parse(id, MSG_NOT_FOUND);
        if (!ScreeningService.isCustomer(user)) {
            throw new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND);
        }
        LoanPlan p = plans.findByPublicId(publicId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
        Long owner = plans.findCustomerId(p.getId()).orElse(null);
        if (owner == null || owner != user.id()) {
            throw new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND);
        }
        return p;
    }

    private Integer productId(ProductCode code) {
        return products.findAll().stream().filter(p -> p.getCode().equals(code.name())).map(LoanProduct::getId).findFirst()
                .orElseThrow(() -> new IllegalStateException("Thiếu gói vay " + code + " trong loan_product."));
    }

    public LoanPlanDto toDto(LoanPlan p) {
        ProductCode code = products.findById(p.getProductId()).map(x -> ProductCode.valueOf(x.getCode()))
                .orElseThrow(() -> new IllegalStateException("Không có gói vay #" + p.getProductId() + "."));
        Map<Integer, CrossSell> cs = simulations.allCrossSells().stream().collect(Collectors.toMap(CrossSell::id, Function.identity()));
        List<LoanPlanDto.CrossSellItem> items = planCrossSells.findByPlanIdOrderByCrossSellId(p.getId()).stream()
                .map(x -> new LoanPlanDto.CrossSellItem(x.getCrossSellId(),
                        cs.containsKey(x.getCrossSellId()) ? cs.get(x.getCrossSellId()).name() : null,
                        x.getExtraCost() == null ? 0 : x.getExtraCost().longValueExact(), x.getBenefit()))
                .toList();
        UUID screeningId = plans.findScreeningPublicId(p.getScreeningId()).orElseThrow();
        return new LoanPlanDto(p.getPublicId(), screeningId, code, Engine.PRODUCT_NAMES.get(code), p.getAmount().longValueExact(),
                p.getTermMonths(), p.getRefRate().doubleValue(), p.getMonthlyPayment().longValueExact(),
                p.getTotalInterest().longValueExact(), p.getDtiAfter().doubleValue(), p.getScore().doubleValue(), p.getStatus(), items,
                p.getCreatedAt(), catalog.checklist(code.name()), plans.findOpenApplicationPublicId(p.getId()).orElse(null));
    }
}
