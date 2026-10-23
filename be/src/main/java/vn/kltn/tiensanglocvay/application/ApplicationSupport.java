package vn.kltn.tiensanglocvay.application;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.kltn.tiensanglocvay.application.Dtos.AppDocument;
import vn.kltn.tiensanglocvay.application.Dtos.ApplicationPermissions;
import vn.kltn.tiensanglocvay.application.Dtos.ApprovalResultDto;
import vn.kltn.tiensanglocvay.application.Dtos.ApplicationSummary;
import vn.kltn.tiensanglocvay.application.Dtos.ExtractedFieldDto;
import vn.kltn.tiensanglocvay.application.Dtos.LoanRequestFormDto;
import vn.kltn.tiensanglocvay.application.Dtos.PersonalInfo;
import vn.kltn.tiensanglocvay.application.Dtos.StatusEvent;
import vn.kltn.tiensanglocvay.application.Dtos.VerificationDetailDto;
import vn.kltn.tiensanglocvay.application.Dtos.VerificationDto;
import vn.kltn.tiensanglocvay.auth.Customer;
import vn.kltn.tiensanglocvay.auth.CustomerRepository;
import vn.kltn.tiensanglocvay.auth.StaffRepository;
import vn.kltn.tiensanglocvay.catalog.Branch;
import vn.kltn.tiensanglocvay.catalog.BranchRepository;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.catalog.DocumentTypeRepository;
import vn.kltn.tiensanglocvay.catalog.LoanProductRepository;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.common.PublicIds;
import vn.kltn.tiensanglocvay.document.StorageService;
import vn.kltn.tiensanglocvay.document.StoredFile;
import vn.kltn.tiensanglocvay.engine.Engine;
import vn.kltn.tiensanglocvay.engine.EngineInput;
import vn.kltn.tiensanglocvay.engine.ProductCode;
import vn.kltn.tiensanglocvay.plan.LoanPlan;
import vn.kltn.tiensanglocvay.plan.LoanPlanDto;
import vn.kltn.tiensanglocvay.plan.LoanPlanRepository;
import vn.kltn.tiensanglocvay.plan.PlanCrossSell;
import vn.kltn.tiensanglocvay.plan.PlanCrossSellRepository;
import vn.kltn.tiensanglocvay.plan.PlanService;
import vn.kltn.tiensanglocvay.policy.ActivePolicy;
import vn.kltn.tiensanglocvay.screening.Consent;
import vn.kltn.tiensanglocvay.screening.ConsentRepository;
import vn.kltn.tiensanglocvay.screening.Screening;
import vn.kltn.tiensanglocvay.screening.ScreeningRepository;
import vn.kltn.tiensanglocvay.screening.ScreeningService;
import vn.kltn.tiensanglocvay.security.AccountKind;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.Role;

/**
 * Dùng chung cho các thao tác hồ sơ: tìm hồ sơ theo quyền (mock {@code own}, {@code canSee}), nạp {@link AppBundle},
 * dựng DTO Application (mock {@code view}) và ApplicationSummary (mock {@code summary}). Gọi trong giao dịch của người dùng.
 */
@Component
public class ApplicationSupport {

    static final String MSG_NOT_FOUND = "Không tìm thấy hồ sơ.";

    private final LoanApplicationRepository apps;
    private final ApplicationDocumentRepository docs;
    private final ExtractedFieldRepository fields;
    private final LoanRequestFormRepository forms;
    private final StatusHistoryRepository history;
    private final DocumentTypeRepository docTypes;
    private final LoanPlanRepository plans;
    private final PlanCrossSellRepository planCrossSells;
    private final PlanService planService;
    private final LoanProductRepository products;
    private final ScreeningRepository screenings;
    private final ScreeningService screeningService;
    private final ConsentRepository consents;
    private final BranchRepository branches;
    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final ActivePolicy policy;
    private final StorageService storage;
    private final VerificationRepository verifications;
    private final VerificationDetailRepository verificationDetails;
    private final ApprovalResultRepository approvals;
    private final Duration previewTtl;

    public ApplicationSupport(LoanApplicationRepository apps, ApplicationDocumentRepository docs, ExtractedFieldRepository fields,
                              LoanRequestFormRepository forms, StatusHistoryRepository history, DocumentTypeRepository docTypes,
                              LoanPlanRepository plans, PlanCrossSellRepository planCrossSells, PlanService planService,
                              LoanProductRepository products, ScreeningRepository screenings, ScreeningService screeningService,
                              ConsentRepository consents, BranchRepository branches, CustomerRepository customers, StaffRepository staff,
                              ActivePolicy policy, StorageService storage, VerificationRepository verifications,
                              VerificationDetailRepository verificationDetails, ApprovalResultRepository approvals,
                              @Value("${app.storage.signed-url-minutes:10}") long signedUrlMinutes) {
        this.apps = apps;
        this.docs = docs;
        this.fields = fields;
        this.forms = forms;
        this.history = history;
        this.docTypes = docTypes;
        this.plans = plans;
        this.planCrossSells = planCrossSells;
        this.planService = planService;
        this.products = products;
        this.screenings = screenings;
        this.screeningService = screeningService;
        this.consents = consents;
        this.branches = branches;
        this.customers = customers;
        this.staff = staff;
        this.policy = policy;
        this.storage = storage;
        this.verifications = verifications;
        this.verificationDetails = verificationDetails;
        this.approvals = approvals;
        this.previewTtl = Duration.ofMinutes(signedUrlMinutes);
    }

    // ---------------------------------------------------------------- tìm hồ sơ

    /** Hồ sơ của chính khách hàng; mã sai, không có, không phải khách hàng hoặc của người khác → 404 (mock {@code own}). */
    public LoanApplication owned(String id, CurrentUser u) {
        UUID publicId = PublicIds.parse(id, MSG_NOT_FOUND);
        return apps.findByPublicId(publicId)
                .filter(a -> isOwner(a, u))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
    }

    /** Chủ hồ sơ, nhân viên chi nhánh tiếp nhận, quản trị viên (mock {@code canSee}); người khác 404. */
    public LoanApplication visible(String id, CurrentUser u) {
        UUID publicId = PublicIds.parse(id, MSG_NOT_FOUND);
        return apps.findByPublicId(publicId)
                .filter(a -> canSee(a, u))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
    }

    static boolean isOwner(LoanApplication a, CurrentUser u) {
        return u != null && u.role() == Role.CUSTOMER && u.id() == a.getCustomerId();
    }

    static boolean isBranchStaff(LoanApplication a, CurrentUser u) {
        return u != null && u.role() == Role.BRANCH_STAFF && u.branchId() != null && u.branchId().longValue() == a.getBranchId();
    }

    static boolean canSee(LoanApplication a, CurrentUser u) {
        return isOwner(a, u) || isBranchStaff(a, u) || (u != null && u.role() == Role.ADMIN);
    }

    /** Tên người thực hiện như {@code u.name} của mock (tên tài khoản). */
    public Actor actor(CurrentUser u) {
        if (u.kind() == AccountKind.CUSTOMER) {
            String name = customers.findById(u.id()).map(ApplicationSupport::customerName).orElse("Khách hàng");
            return new Actor("CUSTOMER", u.id(), name);
        }
        return new Actor("STAFF", u.id(), staffName((int) u.id()));
    }

    public String staffName(Integer staffId) {
        return staffId == null ? "Nhân viên" : staff.findById(staffId).map(s -> s.getFullName()).orElse("Nhân viên");
    }

    public static String customerName(Customer c) {
        if (c.getFullName() != null) {
            return c.getFullName();
        }
        String phone = c.getPhone();
        return "Khách hàng " + phone.substring(Math.max(0, phone.length() - 4));
    }

    // ---------------------------------------------------------------- nạp dữ liệu

    public AppBundle load(LoanApplication app) {
        List<ApplicationDocument> ds = docs.findByApplicationIdOrderById(app.getId());
        Map<Long, List<ExtractedField>> fs = ds.isEmpty() ? Map.of()
                : fields.findByDocumentIdInOrderById(ds.stream().map(ApplicationDocument::getId).toList()).stream()
                        .collect(Collectors.groupingBy(ExtractedField::getDocumentId));
        Map<Integer, DocumentType> types = docTypes.findAll().stream().collect(Collectors.toMap(DocumentType::getId, Function.identity()));
        LoanPlan plan = plans.findById(app.getPlanId()).orElseThrow();
        ProductCode code = products.findById(plan.getProductId()).map(p -> ProductCode.valueOf(p.getCode())).orElseThrow();
        List<Integer> cs = planCrossSells.findByPlanIdOrderByCrossSellId(plan.getId()).stream().map(PlanCrossSell::getCrossSellId).toList();
        Screening s = screenings.findById(plan.getScreeningId()).orElse(null);
        EngineInput input = s == null ? null : screeningService.engineInput(s);
        String phone = customers.findById(app.getCustomerId()).map(Customer::getPhone).orElse("");
        return new AppBundle(app, ds, fs, types, plan, code, cs, s, input, phone, forms.findByApplicationId(app.getId()).orElse(null));
    }

    /** Mock {@code if (app.requestForm) app.requestForm = null}. */
    public void dropForm(AppBundle b) {
        if (b.form != null) {
            forms.delete(b.form);
            forms.flush();
            b.form = null;
        }
    }

    public LoanPlanDto planDto(AppBundle b) {
        return planService.toDto(b.plan);
    }

    public String branchName(LoanApplication app) {
        return branches.findById(app.getBranchId()).map(Branch::getName).orElse("");
    }

    // ---------------------------------------------------------------- DTO

    public Dtos.Application view(AppBundle b, CurrentUser viewer) {
        LoanApplication a = b.app;
        Branch branch = branches.findById(a.getBranchId()).orElse(null);
        boolean owner = isOwner(a, viewer);
        boolean branchStaff = isBranchStaff(a, viewer);
        ApplicationStatus st = a.getStatus();
        ApplicationPermissions can = new ApplicationPermissions(
                owner && ApplicationStatus.EDITABLE.contains(st),
                owner && st == ApplicationStatus.DRAFTING,
                owner && (st == ApplicationStatus.NEED_MORE || st == ApplicationStatus.EXPIRED),
                owner && ApplicationStatus.CANCELLABLE.contains(st),
                branchStaff && st == ApplicationStatus.WAITING_VERIFY,
                branchStaff && st == ApplicationStatus.VERIFIED,
                branchStaff && st == ApplicationStatus.APPROVED);
        List<AppDocument> documents = b.docs.stream().map(d -> document(b, d)).toList();
        LoanRequestFormDto form = b.form == null ? null
                : new LoanRequestFormDto(b.form.getStatus(), b.form.getContent(), b.form.getCreatedAt(), b.form.getOtpConfirmedAt());
        List<StatusEvent> events = history.findByApplicationIdOrderById(a.getId()).stream()
                .map(h -> new StatusEvent(h.getToStatus(), h.getCreatedAt(), h.getActorName(), h.getNote())).toList();
        String versionNo = a.getVersionId() == null ? null : policy.version(a.getVersionId()).versionNo();
        return new Dtos.Application(a.getPublicId(), a.getCode(), a.getCustomerId(), st, a.getBranchId(),
                branch == null ? "" : branch.getName(), branch == null ? "" : branch.getAddress(), planService.toDto(b.plan),
                b.screening == null ? "" : b.screening.getPurpose(), personal(b),
                consents.findById(a.getConsentId()).map(Consent::getAgreedAt).orElse(null), versionNo, a.getContentHash(),
                a.getLockedAt(), a.getQrToken(), a.getExpiresAt(), a.getCreatedAt(), a.getUpdatedAt(), documents, a.getWarnings(),
                a.getRecalc(), form, verifications(b), approval(a), events, can);
    }

    /** Các phiếu đối chiếu theo thứ tự ghi (mock {@code app.verifications.push}). */
    private List<VerificationDto> verifications(AppBundle b) {
        List<Verification> vs = verifications.findByApplicationIdOrderById(b.app.getId());
        if (vs.isEmpty()) {
            return List.of();
        }
        Map<Long, List<VerificationDetail>> details = verificationDetails.findByVerificationIdIn(vs.stream().map(Verification::getId).toList())
                .stream().collect(Collectors.groupingBy(VerificationDetail::getVerificationId));
        Map<Long, ApplicationDocument> docsById = b.docs.stream().collect(Collectors.toMap(ApplicationDocument::getId, Function.identity()));
        Map<Integer, String> names = new HashMap<>();
        return vs.stream().map(v -> new VerificationDto(v.getId(), names.computeIfAbsent(v.getStaffId(), this::staffName),
                v.getOverallResult(), nz(v.getNote()), v.getVerifiedAt(),
                details.getOrDefault(v.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(VerificationDetail::getDocumentId))
                        .map(d -> new VerificationDetailDto(d.getDocumentId(),
                                docsById.containsKey(d.getDocumentId()) ? b.typeName(docsById.get(d.getDocumentId())) : "",
                                d.getResult(), nz(d.getReason())))
                        .toList()))
                .toList();
    }

    /** Kết quả xét duyệt mới nhất, hoặc null. */
    private ApprovalResultDto approval(LoanApplication a) {
        return approvals.findFirstByApplicationIdOrderByIdDesc(a.getId())
                .map(r -> new ApprovalResultDto(r.getId(), r.getResultType(), r.getApprovedAmount(), r.getApprovedTerm(), r.getApprovedRate(),
                        r.getReason(), r.getRecordedAt(), staffName(r.getStaffId()), r.getDisbursedAt(), r.getDisbursedAmount(),
                        r.getBeneficiary()))
                .orElse(null);
    }

    static PersonalInfo personal(AppBundle b) {
        return personal(b.app, b.phone);
    }

    /** Thông tin cá nhân khai cho hồ sơ; {@code phone} là số điện thoại tài khoản khách hàng. */
    public static PersonalInfo personal(LoanApplication a, String phone) {
        return new PersonalInfo(nz(a.getFullName()), nz(a.getNationalId()), a.getDob() == null ? "" : a.getDob().toString(),
                a.getGender() == null ? "MALE" : a.getGender(), nz(a.getAddress()), phone, nz(a.getEmail()));
    }

    private AppDocument document(AppBundle b, ApplicationDocument d) {
        DocumentType t = b.types.get(d.getDocumentTypeId());
        StoredFile stored = d.stored();
        String preview = stored != null && stored.isImage() ? storage.signedUrl(stored, previewTtl) : null;
        String file = stored != null ? storage.fileUrl(stored, previewTtl) : null;
        List<ExtractedFieldDto> fs = b.fieldsOf(d).stream()
                .map(f -> new ExtractedFieldDto(f.getId(), f.getFieldName(), f.getLabel(), nz(f.getOcrValue()),
                        f.getConfidence() == null ? 0 : f.getConfidence().doubleValue(), f.getConfirmedValue()))
                .toList();
        return new AppDocument(d.getId(), d.getDocumentTypeId(), t == null ? "" : t.getName(), t == null ? "" : t.getGroup(),
                d.isRequired(), t == null ? "" : t.getAllowedFormats(), d.getStatus(), d.getFileName(), d.getFileFormat(), d.getFileSize(),
                d.getVersionNo(), d.getIssuedDate(), d.getQualityStatus(), d.getQualityMessage(), d.getUploadedAt(), preview, file, fs,
                d.getVerifyResult(), d.getVerifyReason());
    }

    public ApplicationSummary summary(LoanApplication a) {
        LoanPlan plan = plans.findById(a.getPlanId()).orElseThrow();
        ProductCode code = products.findById(plan.getProductId()).map(p -> ProductCode.valueOf(p.getCode())).orElseThrow();
        String phone = customers.findById(a.getCustomerId()).map(Customer::getPhone).orElse("");
        String name = a.getFullName() == null || a.getFullName().isEmpty() ? "Chưa khai" : a.getFullName();
        return new ApplicationSummary(a.getPublicId(), a.getCode(), name, phone, code, Engine.PRODUCT_NAMES.get(code),
                plan.getAmount().longValueExact(), plan.getTermMonths(), plan.getScore().doubleValue(), branchName(a), a.getStatus(),
                a.getQrToken(), a.getCreatedAt(), a.getUpdatedAt(), a.getExpiresAt());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
