package vn.kltn.tiensanglocvay.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.kltn.tiensanglocvay.admin.AdminDtos.CloneParamVersionRequest;
import vn.kltn.tiensanglocvay.admin.AdminDtos.IdResult;
import vn.kltn.tiensanglocvay.admin.AdminDtos.ParamChange;
import vn.kltn.tiensanglocvay.admin.AdminDtos.ParamInput;
import vn.kltn.tiensanglocvay.admin.AdminDtos.ParamVersionDetail;
import vn.kltn.tiensanglocvay.admin.AdminDtos.ParamVersionDto;
import vn.kltn.tiensanglocvay.admin.AdminDtos.PolicyParamDto;
import vn.kltn.tiensanglocvay.admin.AdminDtos.ScoringWeightDto;
import vn.kltn.tiensanglocvay.admin.AdminDtos.TestRunResult;
import vn.kltn.tiensanglocvay.admin.AdminDtos.TestRunRow;
import vn.kltn.tiensanglocvay.admin.AdminDtos.UpdateParamVersionRequest;
import vn.kltn.tiensanglocvay.admin.AdminDtos.WeightInput;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.auth.Staff;
import vn.kltn.tiensanglocvay.auth.StaffRepository;
import vn.kltn.tiensanglocvay.catalog.LoanProduct;
import vn.kltn.tiensanglocvay.catalog.LoanProductRepository;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.engine.Engine;
import vn.kltn.tiensanglocvay.engine.JsFormat;
import vn.kltn.tiensanglocvay.engine.ParamSet;
import vn.kltn.tiensanglocvay.engine.ProductCode;
import vn.kltn.tiensanglocvay.engine.WeightSet;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.policy.EngineParams;
import vn.kltn.tiensanglocvay.policy.ParamVersion;
import vn.kltn.tiensanglocvay.policy.ParamVersionRepository;
import vn.kltn.tiensanglocvay.policy.PolicyParam;
import vn.kltn.tiensanglocvay.policy.PolicyParamRepository;
import vn.kltn.tiensanglocvay.policy.PolicySnapshot;
import vn.kltn.tiensanglocvay.policy.ScoringWeight;
import vn.kltn.tiensanglocvay.policy.ScoringWeightRepository;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * 7.1, 7.2, 7.4, 7.5 phiên bản tham số (QT07), chép từ fe/src/mocks/handlers/admin.ts. Mọi thao tác ghi khóa dòng phiên bản và chỉ
 * chạy trên bản DRAFT, nên bản ACTIVE/EXPIRED không bao giờ đổi (GET /screenings/{id} tính lại theo phiên bản lúc sàng lọc).
 */
@Service
public class ParamVersionService {

    static final String MSG_NOT_FOUND = "Không tìm thấy phiên bản.";
    static final String MSG_DRAFT_EXISTS = "Đang có một phiên bản nháp, hãy hoàn tất hoặc xóa trước.";
    static final String MSG_DUP_NO = "Số phiên bản đã tồn tại.";
    static final String MSG_EDIT_DRAFT = "Chỉ sửa được phiên bản nháp.";
    static final String MSG_DELETE_DRAFT = "Chỉ xóa được phiên bản nháp.";
    static final String MSG_TEST_DRAFT = "Chỉ chạy thử phiên bản nháp.";
    static final String MSG_ACTIVATE_DRAFT = "Chỉ kích hoạt phiên bản nháp.";
    static final String MSG_NEED_TEST = "Cần chạy thử trên bộ hồ sơ mẫu trước khi kích hoạt.";
    static final String MSG_DTI = "DTI tối đa phải nằm trong khoảng 65%–80%.";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ParamVersionRepository versions;
    private final PolicyParamRepository params;
    private final ScoringWeightRepository weights;
    private final LoanProductRepository products;
    private final StaffRepository staff;
    private final ActivePolicy policy;
    private final ActivityLogService activity;
    private final ObjectMapper json;
    private final Clock clock;

    public ParamVersionService(ParamVersionRepository versions, PolicyParamRepository params, ScoringWeightRepository weights,
                               LoanProductRepository products, StaffRepository staff, ActivePolicy policy, ActivityLogService activity,
                               ObjectMapper json, Clock clock) {
        this.versions = versions;
        this.params = params;
        this.weights = weights;
        this.products = products;
        this.staff = staff;
        this.policy = policy;
        this.activity = activity;
        this.json = json;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- đọc

    @Transactional(readOnly = true)
    public List<ParamVersionDto> list() {
        List<ParamVersion> all = versions.findAllByOrderByIdDesc();
        Map<Integer, String> names = staffNames(all.stream().map(ParamVersion::getCreatedBy).collect(Collectors.toSet()));
        return all.stream().map(v -> dto(v, names)).toList();
    }

    @Transactional(readOnly = true)
    public ParamVersionDetail get(int id) {
        ParamVersion v = versions.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
        Map<Integer, String> codes = productCodes();
        ParamVersionDto d = dto(v, staffNames(Set.of(v.getCreatedBy())));
        List<PolicyParamDto> ps = params.findByVersionIdOrderById(id).stream()
                .map(p -> new PolicyParamDto(p.getId(), p.getProductId() == null ? null : codes.get(p.getProductId()), p.getKey(),
                        PolicyLabels.param(p.getKey()), p.getValue(), p.getUnit())).toList();
        List<ScoringWeightDto> ws = weights.findByVersionIdOrderById(id).stream()
                .map(w -> new ScoringWeightDto(w.getId(), codes.get(w.getProductId()), w.getCriterion(), PolicyLabels.criterion(w.getCriterion()),
                        w.getWeight().doubleValue())).toList();
        return new ParamVersionDetail(d.id(), d.versionNo(), d.legalBasis(), d.status(), d.createdBy(), d.createdAt(), d.activatedAt(), ps, ws,
                readTest(v));
    }

    // ---------------------------------------------------------------- tạo bản nháp

    @Transactional
    public IdResult cloneVersion(int sourceId, CloneParamVersionRequest b, CurrentUser u) {
        if (versions.existsByStatus(ParamVersion.DRAFT)) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_DRAFT_EXISTS);
        }
        ParamVersion src = versions.findById(sourceId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
        if (b == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FieldErrors.INVALID);
        }
        String versionNo = FieldErrors.trim(b.versionNo());
        if (versionNo != null && versions.existsByVersionNo(versionNo)) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_DUP_NO);
        }
        FieldErrors e = new FieldErrors();
        e.text("versionNo", versionNo, 20);
        e.text("legalBasis", b.legalBasis(), 255);
        List<ParamChange> changes = b.changes() == null ? List.of() : b.changes();
        for (int i = 0; i < changes.size(); i++) {
            ParamChange c = changes.get(i);
            if (c == null || c.key() == null || !isNumber(c.value()) || (c.productCode() != null && !isProductCode(c.productCode()))) {
                e.add("changes[" + i + "]", "Cần khóa tham số, mã gói (TD, TC hoặc rỗng) và giá trị là số.");
            }
        }
        e.throwIfAny();

        ParamVersion draft = new ParamVersion(versionNo, b.legalBasis().trim(), (int) u.id(), OffsetDateTime.now(clock));
        try {
            versions.saveAndFlush(draft);
        } catch (DataIntegrityViolationException ex) {
            // Hai quản trị viên tạo bản nháp cùng lúc: unique index uq_param_version_draft hoặc version_no.
            String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
            throw new ApiException(HttpStatus.CONFLICT, cause.contains("uq_param_version_draft") ? MSG_DRAFT_EXISTS : MSG_DUP_NO);
        }
        Map<Integer, String> codes = productCodes();
        for (PolicyParam p : params.findByVersionIdOrderById(src.getId())) {
            String code = p.getProductId() == null ? null : codes.get(p.getProductId());
            String value = changes.stream().filter(c -> c.key().equals(p.getKey()) && Objects.equals(c.productCode(), code))
                    .map(c -> c.value().trim()).findFirst().orElse(p.getValue());
            params.save(new PolicyParam(draft.getId(), p.getProductId(), p.getKey(), value, p.getUnit()));
        }
        for (ScoringWeight w : weights.findByVersionIdOrderById(src.getId())) {
            weights.save(new ScoringWeight(draft.getId(), w.getProductId(), w.getCriterion(), w.getWeight(), w.getAdjustFactor()));
        }
        activity.log(u, "Tạo bản nháp tham số", versionNo, null);
        return new IdResult(draft.getId());
    }

    // ---------------------------------------------------------------- sửa bản nháp

    @Transactional
    public void update(int id, UpdateParamVersionRequest b, CurrentUser u) {
        ParamVersion v = versions.findByIdForUpdate(id).filter(ParamVersion::isDraft)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, MSG_EDIT_DRAFT));
        if (b == null || b.params() == null || b.weights() == null) {
            FieldErrors e = new FieldErrors();
            e.add(b == null || b.params() == null ? "params" : "weights", "Không được để trống.");
            e.throwIfAny();
        }
        // Hai kiểm tra có câu chữ của mock, giữ đúng thứ tự: tổng trọng số từng gói rồi DTI tối đa.
        for (String pc : List.of("TD", "TC")) {
            List<WeightInput> ws = b.weights().stream().filter(w -> w != null && pc.equals(w.productCode())).toList();
            if (ws.stream().anyMatch(w -> w.weight() == null)) {
                continue; // thiếu giá trị: kiểm tra chặt bên dưới báo lỗi trường
            }
            double sum = 0;
            for (WeightInput w : ws) {
                sum += w.weight().doubleValue();
            }
            if (Math.abs(sum - 100) > 0.01) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Tổng trọng số của gói " + pc + " phải bằng 100% (hiện " + JsFormat.num(sum) + "%).");
            }
        }
        b.params().stream().filter(p -> p != null && "DTI_MAX".equals(p.key()) && isNumber(p.value())).findFirst().ifPresent(p -> {
            BigDecimal dti = new BigDecimal(p.value().trim());
            if (dti.compareTo(BigDecimal.valueOf(65)) < 0 || dti.compareTo(BigDecimal.valueOf(80)) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, MSG_DTI);
            }
        });

        // Chặt hơn mock: phải gửi đúng bộ khóa của bản nháp, giá trị là số.
        Map<Integer, String> codes = productCodes();
        FieldErrors e = new FieldErrors();
        Map<String, PolicyParam> current = new HashMap<>();
        for (PolicyParam p : params.findByVersionIdOrderById(id)) {
            current.put(paramKey(p.getProductId() == null ? null : codes.get(p.getProductId()), p.getKey()), p);
        }
        Map<String, String> newValues = new HashMap<>();
        for (int i = 0; i < b.params().size(); i++) {
            ParamInput p = b.params().get(i);
            String k = p == null ? null : paramKey(p.productCode(), p.key());
            if (k == null || !current.containsKey(k) || newValues.containsKey(k)) {
                e.add("params[" + i + "]", "Tham số không thuộc phiên bản hoặc bị trùng.");
            } else if (!isNumber(p.value()) || p.value().trim().length() > 60) {
                e.add("params[" + i + "]", "Giá trị phải là số.");
            } else {
                newValues.put(k, p.value().trim());
            }
        }
        if (newValues.size() < current.size() && e.isEmpty()) {
            e.add("params", "Thiếu tham số của phiên bản.");
        }
        Map<String, ScoringWeight> currentW = new HashMap<>();
        for (ScoringWeight w : weights.findByVersionIdOrderById(id)) {
            currentW.put(paramKey(codes.get(w.getProductId()), w.getCriterion()), w);
        }
        Map<String, BigDecimal> newWeights = new HashMap<>();
        for (int i = 0; i < b.weights().size(); i++) {
            WeightInput w = b.weights().get(i);
            String k = w == null ? null : paramKey(w.productCode(), w.criterion());
            if (k == null || !currentW.containsKey(k) || newWeights.containsKey(k)) {
                e.add("weights[" + i + "]", "Tiêu chí không thuộc phiên bản hoặc bị trùng.");
            } else {
                e.decimal("weights[" + i + "]", w.weight(), BigDecimal.ZERO, HUNDRED, 2);
                newWeights.put(k, w.weight());
            }
        }
        if (newWeights.size() < currentW.size()) {
            e.add("weights", "Thiếu tiêu chí của phiên bản.");
        }
        e.optionalText("legalBasis", b.legalBasis(), 255);
        e.throwIfAny();

        newValues.forEach((k, val) -> current.get(k).setValue(val));
        newWeights.forEach((k, w) -> currentW.get(k).setWeight(w));
        if (b.legalBasis() != null && !b.legalBasis().isBlank()) {
            v.setLegalBasis(b.legalBasis().trim());
        }
        v.setTestResult(null);
        activity.log(u, "Cập nhật bản nháp tham số", v.getVersionNo(), null);
    }

    @Transactional
    public void delete(int id, CurrentUser u) {
        ParamVersion v = versions.findByIdForUpdate(id).filter(ParamVersion::isDraft)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, MSG_DELETE_DRAFT));
        activity.log(u, "Xóa bản nháp tham số", v.getVersionNo(), null);
        params.deleteByVersion(id);
        weights.deleteByVersion(id);
        versions.delete(v);
    }

    // ---------------------------------------------------------------- 7.4 chạy thử, kích hoạt

    @Transactional
    public TestRunResult test(int id, CurrentUser u) {
        ParamVersion v = versions.findByIdForUpdate(id).filter(ParamVersion::isDraft)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, MSG_TEST_DRAFT));
        PolicySnapshot active = policy.current();
        PolicySnapshot draft = policy.load(id);
        ParamSet psA = EngineParams.paramSet(active);
        ParamSet psD = EngineParams.paramSet(draft);
        WeightSet wsA = EngineParams.weightSet(active);
        WeightSet wsD = EngineParams.weightSet(draft);
        LocalDate today = LocalDate.now(clock);
        List<TestRunRow> rows = new ArrayList<>();
        for (SampleProfiles.Profile pr : SampleProfiles.ALL) {
            for (ProductCode code : ProductCode.values()) {
                Engine.Exclusion ea = Engine.exclusion(pr.input(), psA, code, today);
                Engine.Exclusion ed = Engine.exclusion(pr.input(), psD, code, today);
                rows.add(new TestRunRow(pr.name(), code.name(), ea.eligible(), ed.eligible(),
                        ea.eligible() ? Engine.maxSafeAmount(pr.input(), psA, code, ea.term(), psA.product(code).refRate()) : null,
                        ed.eligible() ? Engine.maxSafeAmount(pr.input(), psD, code, ed.term(), psD.product(code).refRate()) : null,
                        ea.eligible() ? Engine.scoreOf(pr.input(), psA, wsA, code, pr.input().desiredAmount(), ea.term(), List.of(), today) : null,
                        ed.eligible() ? Engine.scoreOf(pr.input(), psD, wsD, code, pr.input().desiredAmount(), ed.term(), List.of(), today) : null));
            }
        }
        int changed = (int) rows.stream().filter(r -> r.activeEligible() != r.draftEligible()
                || !Objects.equals(r.activeMaxSafe(), r.draftMaxSafe()) || !Objects.equals(r.activeScore(), r.draftScore())).count();
        TestRunResult res = new TestRunResult(OffsetDateTime.now(clock), changed, rows);
        try {
            v.setTestResult(json.writeValueAsString(res));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
        activity.log(u, "Chạy thử phiên bản tham số", v.getVersionNo() + ": " + changed + " dòng thay đổi", null);
        return res;
    }

    @Transactional
    public ParamVersionDto activate(int id, CurrentUser u) {
        ParamVersion v = versions.findByIdForUpdate(id).filter(ParamVersion::isDraft)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, MSG_ACTIVATE_DRAFT));
        if (v.getTestResult() == null) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_NEED_TEST);
        }
        versions.findActiveForUpdate().ifPresent(old -> {
            old.expire();
            versions.flush(); // unique index uq_param_version_active: bản cũ phải rời ACTIVE trước khi bản mới vào
        });
        v.activate((int) u.id(), OffsetDateTime.now(clock));
        activity.log(u, "Kích hoạt phiên bản tham số", v.getVersionNo(), null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                policy.evict();
            }
        });
        return dto(v, staffNames(Set.of(v.getCreatedBy())));
    }

    // ---------------------------------------------------------------- tiện ích

    private TestRunResult readTest(ParamVersion v) {
        if (v.getTestResult() == null) {
            return null;
        }
        try {
            return json.readValue(v.getTestResult(), TestRunResult.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("test_result của phiên bản " + v.getVersionNo() + " không đọc được.", ex);
        }
    }

    private static ParamVersionDto dto(ParamVersion v, Map<Integer, String> names) {
        return new ParamVersionDto(v.getId(), v.getVersionNo(), v.getLegalBasis(), v.getStatus(),
                names.getOrDefault(v.getCreatedBy(), "Quản trị viên"), v.getCreatedAt(), v.getActivatedAt());
    }

    private Map<Integer, String> staffNames(Set<Integer> ids) {
        return staff.findAllById(new HashSet<>(ids)).stream().collect(Collectors.toMap(Staff::getId, Staff::getFullName));
    }

    private Map<Integer, String> productCodes() {
        return products.findAll().stream().collect(Collectors.toMap(LoanProduct::getId, LoanProduct::getCode));
    }

    private static String paramKey(String productCode, String key) {
        return key == null ? null : (productCode == null ? "" : productCode) + "|" + key;
    }

    private static boolean isProductCode(String s) {
        return "TD".equals(s) || "TC".equals(s);
    }

    static boolean isNumber(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(s.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
