package vn.kltn.tiensanglocvay.screening;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.catalog.LoanProduct;
import vn.kltn.tiensanglocvay.catalog.LoanProductRepository;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.common.PublicIds;
import vn.kltn.tiensanglocvay.engine.CreditHistory;
import vn.kltn.tiensanglocvay.engine.Engine;
import vn.kltn.tiensanglocvay.engine.EngineInput;
import vn.kltn.tiensanglocvay.engine.Gender;
import vn.kltn.tiensanglocvay.engine.ScreeningOutcome;
import vn.kltn.tiensanglocvay.engine.ScreeningResultItem;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.policy.EngineParams;
import vn.kltn.tiensanglocvay.policy.PolicySnapshot;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.Role;

/**
 * QT01 (2.1–2.3): sàng lọc, xem lại, lưu kết quả. Thông báo lỗi lấy đúng chữ trong fe/src/mocks/handlers/screening.ts.
 * MB03 không lưu dạng JSON: {@link #get} chạy lại bộ máy với input đã lưu, phiên bản tham số và ngày của lượt sàng lọc
 * (phiên bản đã dùng không sửa được nên kết quả giống hệt lúc tạo).
 */
@Service
public class ScreeningService {

    static final String MSG_NOT_FOUND = "Không tìm thấy lượt sàng lọc.";
    /** GET /screenings/{id}: dùng chung cho không tồn tại và lượt đã lưu của người khác. */
    static final String MSG_RESULT_NOT_FOUND = "Không tìm thấy kết quả sàng lọc.";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Cột dti decimal(5,2). */
    private static final BigDecimal MAX_DTI = new BigDecimal("999.99");

    private final ScreeningRepository screenings;
    private final OtherDebtRepository debts;
    private final ScreeningResultRepository results;
    private final ConsentRepository consents;
    private final LoanProductRepository products;
    private final ActivePolicy policy;
    private final ActivityLogService activity;
    private final Validator validator;
    private final Clock clock;

    public ScreeningService(ScreeningRepository screenings, OtherDebtRepository debts, ScreeningResultRepository results,
                            ConsentRepository consents, LoanProductRepository products, ActivePolicy policy,
                            ActivityLogService activity, Validator validator, Clock clock) {
        this.screenings = screenings;
        this.debts = debts;
        this.results = results;
        this.consents = consents;
        this.products = products;
        this.policy = policy;
        this.activity = activity;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public ScreeningResponse create(ScreeningRequest r, CurrentUser user) {
        if (r.consentA() == null || !Boolean.TRUE.equals(r.consentA().agreed())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần đồng ý xử lý dữ liệu trước khi sàng lọc.");
        }
        if (r.avgIncome() == null || r.avgIncome() < 0 || r.desiredAmount() == null || r.desiredAmount() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Thông tin thu nhập hoặc số tiền vay không hợp lệ.");
        }
        validate(r);
        PolicySnapshot p = policy.current();
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        ScreeningOutcome o = Engine.screen(r.toEngine(), EngineParams.paramSet(p), EngineParams.weightSet(p), now.toLocalDate());
        Long customerId = isCustomer(user) ? user.id() : null;
        Consent consent = consents.save(new Consent(customerId, Consent.SCOPE_A, r.consentA().docVersion(), now));
        Screening s = screenings.save(new Screening(customerId, p.versionId(), consent.getId(), r, o.disposableIncome(),
                dtiColumn(o.currentDti()), now));
        for (ScreeningRequest.OtherDebtInput d : r.otherDebts()) {
            debts.save(new OtherDebt(s.getId(), d.lender(), d.monthlyPayment()));
        }
        saveResults(s.getId(), o.results());
        return ScreeningResponse.of(s, r, o, p.versionNo());
    }

    /**
     * Không tồn tại, mã sai định dạng, hoặc lượt đã lưu mà người xem không phải chủ: cùng một thông báo 404 để không lộ
     * lượt có tồn tại hay không.
     */
    @Transactional(readOnly = true)
    public ScreeningResponse get(String id, CurrentUser user) {
        Screening s = screenings.findByPublicId(PublicIds.parse(id, MSG_RESULT_NOT_FOUND))
                .filter(x -> usableBy(x, user))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_RESULT_NOT_FOUND));
        return view(s);
    }

    /** 2.3: gắn lượt sàng lọc (và đồng ý phần A) vào tài khoản khách hàng. */
    @Transactional
    public ScreeningResponse save(String id, CurrentUser user) {
        if (!isCustomer(user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập tài khoản khách hàng.");
        }
        Screening s = findFor(PublicIds.parse(id, MSG_NOT_FOUND), user);
        claim(s, user.id());
        activity.log(user, "Lưu kết quả sàng lọc", "Lượt #" + PublicIds.shortCode(s.getPublicId()), null);
        return view(s);
    }

    @Transactional(readOnly = true)
    public List<ScreeningResponse> mine(CurrentUser user) {
        if (!isCustomer(user)) {
            return List.of();
        }
        return screenings.findByCustomerIdOrderByIdDesc(user.id()).stream().map(this::view).toList();
    }

    // ---------------------------------------------------------------- dùng chung với mô phỏng, phương án

    /**
     * Lượt sàng lọc mà người gọi được thao tác (mô phỏng, lưu, lập phương án): chưa thuộc ai, hoặc đã thuộc chính khách hàng đó.
     * Không có hoặc đã lưu vào tài khoản khác trả 404 "Không tìm thấy lượt sàng lọc.".
     */
    public Screening findFor(UUID publicId, CurrentUser user) {
        return screenings.findByPublicId(publicId)
                .filter(s -> usableBy(s, user))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
    }

    /** Lượt chưa lưu vào tài khoản nào thì ai có mã cũng dùng được; đã lưu thì chỉ chủ tài khoản. */
    static boolean usableBy(Screening s, CurrentUser user) {
        return s.getCustomerId() == null || (isCustomer(user) && s.getCustomerId() == user.id());
    }

    public static boolean isCustomer(CurrentUser user) {
        return user != null && user.role() == Role.CUSTOMER;
    }

    public EngineInput engineInput(Screening s) {
        return input(s).toEngine();
    }

    /** Gắn lượt sàng lọc và đồng ý phần A của nó vào khách hàng (trong giao dịch của người gọi). */
    public void claim(Screening s, long customerId) {
        s.setCustomerId(customerId);
        consents.findById(s.getConsentId()).ifPresent(c -> c.setCustomerId(customerId));
    }

    /** Ngày (giờ Việt Nam) làm "hôm nay" khi tính lại MB03 của lượt sàng lọc. */
    static LocalDate dayOf(Screening s) {
        return s.getCreatedAt().atZoneSameInstant(VN).toLocalDate();
    }

    /** Kiểm tra bean sau các bước kiểm tra riêng của mock; lỗi trả 400 "Dữ liệu không hợp lệ." kèm {@code fields}. */
    public <T> void validate(T body) {
        Set<ConstraintViolation<T>> v = validator.validate(body);
        if (!v.isEmpty()) {
            throw new ConstraintViolationException(v);
        }
    }

    // ---------------------------------------------------------------- nội bộ

    private ScreeningResponse view(Screening s) {
        ScreeningRequest input = input(s);
        PolicySnapshot p = policy.version(s.getVersionId());
        ScreeningOutcome o = Engine.screen(input.toEngine(), EngineParams.paramSet(p), EngineParams.weightSet(p), dayOf(s));
        return ScreeningResponse.of(s, input, o, p.versionNo());
    }

    private ScreeningRequest input(Screening s) {
        String docVersion = consents.findById(s.getConsentId()).map(Consent::getDocVersion).orElse(null);
        List<ScreeningRequest.OtherDebtInput> other = debts.findByScreeningIdOrderById(s.getId()).stream()
                .map(d -> new ScreeningRequest.OtherDebtInput(d.getLender(), d.getMonthlyPayment().longValueExact())).toList();
        return new ScreeningRequest(new ConsentInput(true, docVersion), s.getNationality(), s.getDob(), Gender.valueOf(s.getGender()),
                MaritalStatus.valueOf(s.getMaritalStatus()), s.getDependents(), s.getAvgIncome().longValueExact(), s.getSalaryBank(),
                s.isAbbankStaff(), CreditHistory.valueOf(s.getCreditHistory()), other, s.getPurpose(),
                s.getDesiredAmount().longValueExact(), s.getDesiredTerm());
    }

    private void saveResults(Long screeningId, List<ScreeningResultItem> items) {
        List<LoanProduct> all = products.findAll();
        for (ScreeningResultItem i : items) {
            Integer productId = all.stream().filter(p -> p.getCode().equals(i.productCode().name())).map(LoanProduct::getId)
                    .findFirst().orElse(null);
            if (productId == null) {
                continue;
            }
            String reason = i.reasons().isEmpty() ? null : String.join(" ", i.reasons());
            if (reason != null && reason.length() > 255) {
                reason = reason.substring(0, 255);
            }
            results.save(new ScreeningResult(screeningId, productId, i.eligible(), reason, i.dti() == null ? null : dtiColumn(i.dti()),
                    i.maxSafeAmount(), i.score(), i.rankNo()));
        }
    }

    private static BigDecimal dtiColumn(double dti) {
        BigDecimal v = BigDecimal.valueOf(dti).setScale(2, RoundingMode.HALF_UP);
        return v.compareTo(MAX_DTI) > 0 ? MAX_DTI : v;
    }
}
