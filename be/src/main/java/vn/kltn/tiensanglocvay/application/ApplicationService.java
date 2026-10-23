package vn.kltn.tiensanglocvay.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.application.Dtos.ConfirmFieldsRequest;
import vn.kltn.tiensanglocvay.application.Dtos.CreateApplicationRequest;
import vn.kltn.tiensanglocvay.application.Dtos.PersonalInfo;
import vn.kltn.tiensanglocvay.application.Dtos.UpdatePlanRequest;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.auth.OtpService;
import vn.kltn.tiensanglocvay.catalog.Branch;
import vn.kltn.tiensanglocvay.catalog.BranchRepository;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.catalog.LoanProductRepository;
import vn.kltn.tiensanglocvay.catalog.ProductDocumentTypeRepository;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.common.Hashing;
import vn.kltn.tiensanglocvay.engine.CrossSell;
import vn.kltn.tiensanglocvay.engine.EngineInput;
import vn.kltn.tiensanglocvay.engine.JsFormat;
import vn.kltn.tiensanglocvay.engine.SimulationResponse;
import vn.kltn.tiensanglocvay.notification.NotificationService;
import vn.kltn.tiensanglocvay.plan.LoanPlan;
import vn.kltn.tiensanglocvay.plan.LoanPlanRepository;
import vn.kltn.tiensanglocvay.plan.PlanCrossSell;
import vn.kltn.tiensanglocvay.plan.PlanCrossSellRepository;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.policy.PolicySnapshot;
import vn.kltn.tiensanglocvay.screening.Consent;
import vn.kltn.tiensanglocvay.screening.ConsentRepository;
import vn.kltn.tiensanglocvay.screening.Screening;
import vn.kltn.tiensanglocvay.screening.ScreeningRepository;
import vn.kltn.tiensanglocvay.screening.ScreeningService;
import vn.kltn.tiensanglocvay.screening.SimulationService;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * QT03 (4.1–4.2, 6.2): lập, khai, xác nhận, nộp, hủy hồ sơ vay. Hành vi, thứ tự kiểm tra và câu chữ theo
 * fe/src/mocks/handlers/applications.ts; tải giấy tờ ở {@link DocumentUploadService}.
 */
@Service
public class ApplicationService {

    private static final DateTimeFormatter CODE_MONTH = DateTimeFormatter.ofPattern("yyMM");
    private static final String QR_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern WORD = Pattern.compile("\\S+");
    private static final String MSG_LOCKED = "Hồ sơ đã khóa.";

    private final LoanApplicationRepository apps;
    private final ApplicationDocumentRepository docs;
    private final LoanRequestFormRepository forms;
    private final ApplicationSupport support;
    private final ApplicationStateMachine states;
    private final CrossCheckService checks;
    private final RequestFormBuilder formBuilder;
    private final LoanPlanRepository plans;
    private final PlanCrossSellRepository planCrossSells;
    private final LoanProductRepository products;
    private final ProductDocumentTypeRepository productDocs;
    private final ScreeningRepository screenings;
    private final ScreeningService screeningService;
    private final SimulationService simulations;
    private final ConsentRepository consents;
    private final BranchRepository branches;
    private final ActivePolicy policy;
    private final OtpService otp;
    private final NotificationService notifications;
    private final ActivityLogService activity;
    private final ObjectMapper json;
    private final Clock clock;

    public ApplicationService(LoanApplicationRepository apps, ApplicationDocumentRepository docs, LoanRequestFormRepository forms,
                              ApplicationSupport support, ApplicationStateMachine states, CrossCheckService checks,
                              RequestFormBuilder formBuilder, LoanPlanRepository plans, PlanCrossSellRepository planCrossSells,
                              LoanProductRepository products, ProductDocumentTypeRepository productDocs, ScreeningRepository screenings,
                              ScreeningService screeningService, SimulationService simulations, ConsentRepository consents,
                              BranchRepository branches, ActivePolicy policy, OtpService otp, NotificationService notifications,
                              ActivityLogService activity, ObjectMapper json, Clock clock) {
        this.apps = apps;
        this.docs = docs;
        this.forms = forms;
        this.support = support;
        this.states = states;
        this.checks = checks;
        this.formBuilder = formBuilder;
        this.plans = plans;
        this.planCrossSells = planCrossSells;
        this.products = products;
        this.productDocs = productDocs;
        this.screenings = screenings;
        this.screeningService = screeningService;
        this.simulations = simulations;
        this.consents = consents;
        this.branches = branches;
        this.policy = policy;
        this.otp = otp;
        this.notifications = notifications;
        this.activity = activity;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Dtos.ApplicationSummary> mine(CurrentUser u) {
        if (!ScreeningService.isCustomer(u)) {
            return List.of();
        }
        return apps.findByCustomerIdOrderByIdDesc(u.id()).stream().map(support::summary).toList();
    }

    /** 4.1: tạo hồ sơ DRAFTING, ghi đồng ý phần B, sinh danh mục giấy tờ MB05 theo gói. */
    @Transactional
    public Dtos.Application create(CreateApplicationRequest b, CurrentUser u) {
        if (b == null || b.consentB() == null || !Boolean.TRUE.equals(b.consentB().agreed())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần đồng ý lưu trữ giấy tờ và lập hồ sơ.");
        }
        screeningService.validate(b.consentB());
        LoanPlan plan = (b.planId() == null ? null : plans.findByPublicId(b.planId())
                .filter(p -> plans.findCustomerId(p.getId()).map(c -> c == u.id()).orElse(false)).orElse(null));
        if (plan == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy phương án.");
        }
        if (!LoanPlan.CONFIRMED.equals(plan.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Cần xác nhận phương án trước khi lập hồ sơ.");
        }
        if (apps.existsByPlanIdAndStatusNot(plan.getId(), ApplicationStatus.CANCELLED)) {
            throw new ApiException(HttpStatus.CONFLICT, "Phương án này đã có hồ sơ.");
        }
        // Khóa dòng chi nhánh (FOR UPDATE) đến hết giao dịch rồi mới kiểm tra ACTIVE: ngừng hoạt động chi nhánh (AdminBranchService.deactivate)
        // khóa cùng dòng này, nên hồ sơ mới không thể vào chi nhánh vừa bị ngừng và cũng không lọt khỏi danh sách hồ sơ được chuyển.
        Branch branch = b.branchId() == null ? null : branches.findByIdForUpdate(b.branchId().intValue())
                .filter(Branch::isActive).orElse(null);
        if (branch == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chi nhánh không hợp lệ.");
        }
        OffsetDateTime now = now();
        Screening scr = screenings.findById(plan.getScreeningId()).orElse(null);
        Consent consent = consents.save(new Consent(u.id(), "B", b.consentB().docVersion(), now));
        LoanApplication app = apps.save(new LoanApplication(newCode(now), u.id(), plan.getId(), branch.getId(), consent.getId(),
                policy.current().versionId(), scr == null ? null : scr.getDob(), scr == null ? "MALE" : scr.getGender(), now));
        String productCode = products.findById(plan.getProductId()).orElseThrow().getCode();
        List<Object[]> rules = new ArrayList<>(productDocs.findRulesByProductCode(productCode));
        // Giấy đề nghị vay vốn hệ thống tự lập đứng cuối danh mục (như mock).
        rules.sort((x, y) -> Boolean.compare(isSystem(x), isSystem(y)));
        for (Object[] r : rules) {
            docs.save(new ApplicationDocument(app.getId(), ((DocumentType) r[0]).getId(), (Boolean) r[1]));
        }
        states.created(app, support.actor(u));
        // Mock ghi `Chi nhánh ${branch.name}` nhưng tên chi nhánh đã có chữ "Chi nhánh"; mock đã sửa cùng (chỉ ghi tên).
        activity.log(u, "Tạo hồ sơ vay", branch.getName(), app.getId());
        return support.view(support.load(app), u);
    }

    private static boolean isSystem(Object[] rule) {
        return ((DocumentType) rule[0]).getId() == DocumentType.SYSTEM_DOC_TYPE;
    }

    /** Mã HS<yyMM>-<500+n> theo tháng hiện tại (giờ Việt Nam), n = số hồ sơ đã có trong tháng + 1 (như mock). */
    private String newCode(OffsetDateTime now) {
        String prefix = "HS" + now.format(CODE_MONTH);
        long n = apps.countByCodeStartingWith(prefix) + 1;
        return prefix + "-" + String.format("%04d", 500 + n);
    }

    @Transactional
    public Dtos.Application get(String id, CurrentUser u) {
        LoanApplication app = support.visible(id, u);
        if (!ScreeningService.isCustomer(u)) {
            activity.log(u, "Xem hồ sơ", "Xem chi tiết hồ sơ", app.getId());
        }
        return support.view(support.load(app), u);
    }

    @Transactional
    public Dtos.Application updatePersonal(String id, PersonalInfo b, CurrentUser u) {
        LoanApplication app = editableOwned(id, u, "Hồ sơ đã khóa, không sửa được.");
        if (b == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ.");
        }
        if (b.nationalId() != null && !b.nationalId().isEmpty() && !b.nationalId().matches("\\d{12}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Số CCCD phải gồm 12 chữ số.");
        }
        Map<String, String> bad = new LinkedHashMap<>();
        tooLong(bad, "fullName", b.fullName(), 100);
        tooLong(bad, "address", b.address(), 255);
        tooLong(bad, "email", b.email(), 100);
        LocalDate dob = null;
        if (b.dob() != null && !b.dob().isEmpty()) {
            try {
                dob = LocalDate.parse(b.dob());
            } catch (DateTimeParseException e) {
                bad.put("dob", "Ngày không hợp lệ (yyyy-MM-dd).");
            }
        }
        if (b.gender() != null && !b.gender().equals("MALE") && !b.gender().equals("FEMALE")) {
            bad.put("gender", "Giá trị không hợp lệ.");
        }
        if (!bad.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ.", bad);
        }
        // Mock gộp {...app.personal, ...b, phone: u.username}: trường không gửi giữ nguyên, phone luôn theo tài khoản.
        if (b.fullName() != null) {
            app.setFullName(b.fullName());
        }
        if (b.nationalId() != null) {
            app.setNationalId(b.nationalId());
        }
        if (b.dob() != null) {
            app.setDob(dob);
        }
        if (b.gender() != null) {
            app.setGender(b.gender());
        }
        if (b.address() != null) {
            app.setAddress(b.address());
        }
        if (b.email() != null) {
            app.setEmail(b.email());
        }
        AppBundle bundle = support.load(app);
        support.dropForm(bundle);
        changed(bundle);
        activity.log(u, "Cập nhật thông tin cá nhân", "", app.getId());
        return support.view(bundle, u);
    }

    /** Xác nhận MB06; với CCCD điền sẵn họ tên, số CCCD, địa chỉ còn trống. */
    @Transactional
    public Dtos.Application confirmFields(String id, long docId, ConfirmFieldsRequest b, CurrentUser u) {
        LoanApplication app = editableOwned(id, u, MSG_LOCKED);
        AppBundle bundle = support.load(app);
        ApplicationDocument d = bundle.docs.stream().filter(x -> x.getId() == docId).findFirst().orElse(null);
        if (d == null || ApplicationDocument.MISSING.equals(d.getStatus()) || ApplicationDocument.REJECTED_QUALITY.equals(d.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Giấy tờ chưa có thông tin trích xuất.");
        }
        List<ConfirmFieldsRequest.FieldValue> sent = b == null || b.fields() == null ? List.of() : b.fields();
        OffsetDateTime now = now();
        for (ExtractedField f : bundle.fieldsOf(d)) {
            ConfirmFieldsRequest.FieldValue x = sent.stream().filter(y -> y != null && y.id() != null && y.id().equals(f.getId()))
                    .findFirst().orElse(null);
            f.confirm(x != null ? truncate(x.confirmedValue(), 255) : f.getOcrValue(), now);
        }
        d.confirm();
        if (d.getDocumentTypeId() == CrossCheckService.CCCD) {
            String name = confirmed(bundle, d, "full_name");
            String nid = confirmed(bundle, d, "national_id");
            String address = confirmed(bundle, d, "address");
            if (isEmpty(app.getFullName()) && !name.isEmpty()) {
                app.setFullName(truncate(capitalizeWords(name), 100));
            }
            // Cột national_id chỉ 12 ký tự; số sai định dạng vẫn bị đối chiếu báo BLOCKING.
            if (isEmpty(app.getNationalId()) && nid.matches("\\d{12}")) {
                app.setNationalId(nid);
            }
            if (isEmpty(app.getAddress()) && !address.isEmpty()) {
                app.setAddress(truncate(address, 255));
            }
        }
        support.dropForm(bundle);
        changed(bundle);
        activity.log(u, "Xác nhận thông tin trích xuất", bundle.typeName(d), app.getId());
        return support.view(bundle, u);
    }

    /** Điều chỉnh phương án khi giá trị kiểm chứng làm phương án không đạt; dùng thu nhập kiểm chứng nếu có. */
    @Transactional
    public Dtos.Application updatePlan(String id, UpdatePlanRequest b, CurrentUser u) {
        LoanApplication app = editableOwned(id, u, MSG_LOCKED);
        AppBundle bundle = support.load(app);
        if (bundle.input == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Không có dữ liệu sàng lọc gốc.");
        }
        Map<String, String> bad = new LinkedHashMap<>();
        if (b == null || b.amount() == null || b.amount() < 0 || b.amount() > 999_999_999_999_999L) {
            bad.put("amount", "Số tiền không hợp lệ.");
        }
        if (b == null || b.termMonths() == null || b.termMonths() < 1) {
            bad.put("termMonths", "Kỳ hạn phải lớn hơn 0.");
        }
        if (b == null || b.crossSellIds() == null || b.crossSellIds().stream().anyMatch(x -> x == null)) {
            bad.put("crossSellIds", "Không được để trống.");
        }
        if (!bad.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ.", bad);
        }
        EngineInput inp = app.getRecalc() != null ? CrossCheckService.withIncome(bundle.input, app.getRecalc().verifiedIncome()) : bundle.input;
        SimulationResponse sim = checks.simulate(inp, bundle, b.amount(), b.termMonths(), b.crossSellIds());
        if (!sim.ok()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Phương án chưa đạt: " + String.join(" ", sim.issues()));
        }
        LoanPlan plan = bundle.plan;
        plan.adjust(sim.amount(), sim.termMonths(), sim.rate(), sim.firstPayment(), sim.totalInterest(), sim.dtiAfter(), sim.score());
        planCrossSells.deleteAll(planCrossSells.findByPlanIdOrderByCrossSellId(plan.getId()));
        planCrossSells.flush();
        for (CrossSell c : simulations.allCrossSells()) {
            if (b.crossSellIds().contains(c.id())) {
                planCrossSells.save(new PlanCrossSell(plan.getId(), c.id(), c.estCost(),
                        "Giảm " + JsFormat.num(c.rateDiscount()) + "%/năm lãi suất tham chiếu, +" + JsFormat.num(c.scoreBonus()) + " điểm ưu tiên"));
            }
        }
        AppBundle fresh = support.load(app);
        support.dropForm(fresh);
        changed(fresh);
        activity.log(u, "Điều chỉnh phương án trong hồ sơ", JsFormat.vnd(sim.amount()) + " đ, " + sim.termMonths() + " tháng", app.getId());
        return support.view(fresh, u);
    }

    /** 4.2: lập giấy đề nghị MB15 tự điền, trạng thái DRAFT. */
    @Transactional
    public Dtos.Application createRequestForm(String id, CurrentUser u) {
        LoanApplication app = editableOwned(id, u, MSG_LOCKED);
        AppBundle bundle = support.load(app);
        List<String> miss = checks.missingForSubmit(bundle);
        if (!miss.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, String.join(" ", miss));
        }
        List<LoanRequestForm.Item> content = formBuilder.build(bundle, support.branchName(app));
        support.dropForm(bundle);
        bundle.form = forms.save(new LoanRequestForm(app.getId(), content, now()));
        activity.log(u, "Lập giấy đề nghị vay vốn", "Bản nháp giấy đề nghị", app.getId());
        return support.view(bundle, u);
    }

    /** Sửa các ô editable của bản nháp; khóa khác bị bỏ qua. */
    @Transactional
    public Dtos.Application updateRequestForm(String id, Dtos.UpdateRequestFormRequest b, CurrentUser u) {
        LoanApplication app = support.owned(id, u);
        AppBundle bundle = support.load(app);
        if (bundle.form == null || !LoanRequestForm.DRAFT.equals(bundle.form.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Chưa có bản nháp giấy đề nghị.");
        }
        Map<String, String> values = b == null || b.values() == null ? Map.of() : b.values();
        bundle.form.setContent(bundle.form.getContent().stream()
                .map(c -> c.editable() && values.get(c.key()) != null
                        ? new LoanRequestForm.Item(c.label(), c.key(), truncate(values.get(c.key()), 500), true) : c)
                .toList());
        return support.view(bundle, u);
    }

    /** Xác nhận giấy đề nghị bằng OTP, khóa nội dung, cấp QR và hạn, chuyển Chờ đối chiếu (cũng dùng để nộp lại). */
    @Transactional
    public Dtos.Application submit(String id, Dtos.OtpBody b, CurrentUser u) {
        LoanApplication app = support.owned(id, u);
        AppBundle bundle = support.load(app);
        long otpId = otp.check(bundle.phone, OtpService.PURPOSE_CONFIRM, b == null ? null : b.otp());
        ApplicationStatus st = app.getStatus();
        boolean resub = st == ApplicationStatus.NEED_MORE || st == ApplicationStatus.EXPIRED;
        if (st != ApplicationStatus.DRAFTING && !resub) {
            throw new ApiException(HttpStatus.CONFLICT, "Hồ sơ không ở trạng thái nộp được.");
        }
        List<String> miss = new ArrayList<>(checks.missingForSubmit(bundle));
        PolicySnapshot ps = policy.current();
        int days = validityDays(ps);
        OffsetDateTime now = now();
        if (resub) {
            List<String> pending = bundle.docs.stream()
                    .filter(d -> "MISMATCH".equals(d.getVerifyResult()) || "MISSING".equals(d.getVerifyResult()))
                    .map(bundle::typeName).toList();
            if (!pending.isEmpty()) {
                miss.add("Cần tải lại: " + String.join(", ", pending) + ".");
            }
            if (st == ApplicationStatus.EXPIRED) {
                List<String> old = bundle.docs.stream()
                        .filter(d -> d.getDocumentTypeId() != DocumentType.SYSTEM_DOC_TYPE && ApplicationDocument.CONFIRMED.equals(d.getStatus())
                                && d.getUploadedAt() != null && d.getUploadedAt().plusDays(days).isBefore(now))
                        .map(bundle::typeName).toList();
                if (!old.isEmpty()) {
                    miss.add("Giấy tờ đã quá hạn hiệu lực, cần tải bản mới: " + String.join(", ", old) + ".");
                }
            }
        }
        if (!miss.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, String.join(" ", miss));
        }
        if (bundle.form == null) {
            bundle.form = forms.save(new LoanRequestForm(app.getId(), formBuilder.build(bundle, support.branchName(app)), now));
        }
        bundle.form.confirmByOtp(now);
        bundle.docs.stream().filter(d -> d.getDocumentTypeId() == DocumentType.SYSTEM_DOC_TYPE).findFirst()
                .ifPresent(d -> d.markSystemGenerated(app.getCode() + "-giay-de-nghi.pdf", now));
        app.lock(contentHash(bundle), now, newQr(), now.plusDays(days), ps.versionId());
        Actor actor = support.actor(u);
        if (st == ApplicationStatus.EXPIRED) {
            states.transition(app, ApplicationStatus.NEED_MORE, actor, "Cập nhật giấy tờ hết hạn");
        }
        states.transition(app, ApplicationStatus.WAITING_VERIFY, actor,
                resub ? "Bổ sung giấy tờ, nộp lại" : "Xác nhận giấy đề nghị bằng OTP, nộp hồ sơ");
        String branchName = support.branchName(app);
        notifications.toBranch(app.getBranchId(), resub ? "Hồ sơ đã bổ sung" : "Có hồ sơ mới chờ đối chiếu",
                app.getCode() + " – " + nz(app.getFullName()), "/noi-bo/ho-so/" + app.getPublicId());
        notifications.toCustomer(app.getCustomerId(), "Đã nộp hồ sơ", "Hồ sơ " + app.getCode() + " đang chờ đối chiếu tại " + branchName
                + ". Mang bản gốc giấy tờ và Phiếu tóm tắt khi đến quầy.", "/ho-so/" + app.getPublicId());
        activity.log(u, resub ? "Nộp lại hồ sơ" : "Nộp hồ sơ", "Khóa nội dung, mã băm " + app.getContentHash().substring(0, 12) + "…",
                app.getId());
        otp.consume(otpId);
        return support.view(bundle, u);
    }

    /** 6.2: hủy hồ sơ (DRAFTING, WAITING_VERIFY, NEED_MORE, EXPIRED). */
    @Transactional
    public Dtos.Application cancel(String id, Dtos.CancelRequest b, CurrentUser u) {
        LoanApplication app = support.owned(id, u);
        if (!ApplicationStatus.CANCELLABLE.contains(app.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "Hồ sơ đang ở trạng thái “" + app.getStatus().label()
                    + "”, không hủy trực tuyến được. Vui lòng liên hệ chi nhánh.");
        }
        String reason = b == null || b.reason() == null ? "" : truncate(b.reason(), 255);
        states.transition(app, ApplicationStatus.CANCELLED, support.actor(u), reason.isEmpty() ? "Khách hàng hủy hồ sơ" : reason);
        app.setCancelReason(reason.isEmpty() ? null : reason);
        notifications.toBranch(app.getBranchId(), "Hồ sơ đã bị hủy", app.getCode() + " – khách hàng hủy hồ sơ",
                "/noi-bo/ho-so/" + app.getPublicId());
        activity.log(u, "Hủy hồ sơ", reason, app.getId());
        return support.view(support.load(app), u);
    }

    // ---------------------------------------------------------------- nội bộ

    public LoanApplication editableOwned(String id, CurrentUser u, String lockedMessage) {
        LoanApplication app = support.owned(id, u);
        if (!ApplicationStatus.EDITABLE.contains(app.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, lockedMessage);
        }
        return app;
    }

    /** Sau mỗi thay đổi nội dung: chạy lại đối chiếu và cập nhật thời điểm (mock {@code refreshChecks(app); app.updatedAt = …}). */
    public void changed(AppBundle bundle) {
        checks.refresh(bundle);
        bundle.app.touch(now());
    }

    /** SHA-256 của {@code {form, plan, docs: [[id, versionNo, [giá trị…]]…]}} (quy-tac-tinh-toan mục 11). */
    private String contentHash(AppBundle b) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("form", b.form.getContent());
        body.put("plan", support.planDto(b));
        body.put("docs", b.docs.stream().map(d -> List.of(d.getId(), d.getVersionNo(),
                b.fieldsOf(d).stream().map(f -> nz(f.value())).collect(Collectors.toList()))).toList());
        try {
            return Hashing.sha256Hex(json.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int validityDays(PolicySnapshot ps) {
        try {
            int d = ps.common("DOC_VALIDITY_DAYS").intValue();
            return d > 0 ? d : 30;
        } catch (IllegalStateException e) {
            return 30; // mock: paramValue('DOC_VALIDITY_DAYS') || 30
        }
    }

    private static String newQr() {
        StringBuilder b = new StringBuilder("QR");
        for (int i = 0; i < 8; i++) {
            b.append(QR_CHARS.charAt(RANDOM.nextInt(QR_CHARS.length())));
        }
        return b.toString();
    }

    private static String confirmed(AppBundle b, ApplicationDocument d, String name) {
        return b.fieldsOf(d).stream().filter(f -> f.getFieldName().equals(name)).findFirst()
                .map(ExtractedField::getConfirmedValue).map(ApplicationService::nz).orElse("");
    }

    /** Mock {@code replace(/\S+/g, w => w[0] + w.slice(1).toLowerCase())}. */
    static String capitalizeWords(String s) {
        Matcher m = WORD.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String w = m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(w.charAt(0) + w.substring(1).toLowerCase(Locale.ROOT)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static void tooLong(Map<String, String> bad, String field, String value, int max) {
        if (value != null && value.length() > max) {
            bad.put(field, "Tối đa " + max + " ký tự.");
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
