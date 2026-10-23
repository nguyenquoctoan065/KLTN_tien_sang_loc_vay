package vn.kltn.tiensanglocvay.staff;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.application.AppBundle;
import vn.kltn.tiensanglocvay.application.ApplicationDocument;
import vn.kltn.tiensanglocvay.application.ApplicationStateMachine;
import vn.kltn.tiensanglocvay.application.ApplicationStatus;
import vn.kltn.tiensanglocvay.application.ApplicationSupport;
import vn.kltn.tiensanglocvay.application.ApprovalResult;
import vn.kltn.tiensanglocvay.application.ApprovalResultRepository;
import vn.kltn.tiensanglocvay.application.Dtos;
import vn.kltn.tiensanglocvay.application.LoanApplication;
import vn.kltn.tiensanglocvay.application.LoanApplicationRepository;
import vn.kltn.tiensanglocvay.application.Verification;
import vn.kltn.tiensanglocvay.application.VerificationDetail;
import vn.kltn.tiensanglocvay.application.VerificationDetailRepository;
import vn.kltn.tiensanglocvay.application.VerificationRepository;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.common.PublicIds;
import vn.kltn.tiensanglocvay.engine.JsFormat;
import vn.kltn.tiensanglocvay.notification.NotificationService;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.staff.StaffDtos.DecisionRequest;
import vn.kltn.tiensanglocvay.staff.StaffDtos.DisbursementRequest;
import vn.kltn.tiensanglocvay.staff.StaffDtos.PublicIdResult;
import vn.kltn.tiensanglocvay.staff.StaffDtos.StaffSummary;
import vn.kltn.tiensanglocvay.staff.StaffDtos.VerificationRequest;

/**
 * Nghiệp vụ nhân viên chi nhánh, chép từ fe/src/mocks/handlers/staff.ts (thông báo lỗi, ghi chú lịch sử, thông báo, nhật ký).
 * Vai trò BRANCH_STAFF đã được {@code @RequireRole} kiểm tra ở controller; ở đây chỉ còn kiểm tra chi nhánh của hồ sơ.
 */
@Service
public class StaffService {

    static final String MSG_NOT_FOUND = "Không tìm thấy hồ sơ của chi nhánh.";
    private static final String MSG_INVALID = "Dữ liệu không hợp lệ.";
    private static final Set<String> VERIFY_RESULTS = Set.of(Verification.MATCH, Verification.MISMATCH, Verification.MISSING);
    private static final Set<String> RESULT_TYPES = Set.of(ApprovalResult.APPROVED, ApprovalResult.REJECTED, ApprovalResult.MORE_INFO);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final long MAX_AMOUNT = 999_999_999_999_999L;
    private static final BigDecimal MAX_RATE = new BigDecimal("999.99");

    private final LoanApplicationRepository apps;
    private final ApplicationSupport support;
    private final ApplicationStateMachine states;
    private final VerificationRepository verifications;
    private final VerificationDetailRepository verificationDetails;
    private final ApprovalResultRepository approvals;
    private final NotificationService notifications;
    private final ActivityLogService activity;
    private final Clock clock;

    public StaffService(LoanApplicationRepository apps, ApplicationSupport support, ApplicationStateMachine states,
                        VerificationRepository verifications, VerificationDetailRepository verificationDetails,
                        ApprovalResultRepository approvals, NotificationService notifications, ActivityLogService activity, Clock clock) {
        this.apps = apps;
        this.support = support;
        this.states = states;
        this.verifications = verifications;
        this.verificationDetails = verificationDetails;
        this.approvals = approvals;
        this.notifications = notifications;
        this.activity = activity;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 5.1 tra cứu

    @Transactional(readOnly = true)
    public List<Dtos.ApplicationSummary> list(String status, String keyword, CurrentUser u) {
        Set<ApplicationStatus> statuses = EnumSet.noneOf(ApplicationStatus.class);
        boolean anyStatus = status == null || status.isEmpty();
        if (!anyStatus) {
            for (String s : status.split(",")) {
                try {
                    statuses.add(ApplicationStatus.valueOf(s));
                } catch (IllegalArgumentException e) {
                    // Mock: trạng thái lạ không khớp hồ sơ nào.
                }
            }
            if (statuses.isEmpty()) {
                return List.of();
            }
        } else {
            statuses.add(ApplicationStatus.DRAFTING); // tham số IN không được rỗng; bị bỏ qua vì anyStatus
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String like = "%" + kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return apps.searchBranch(branchOf(u), ApplicationStatus.DRAFTING, anyStatus, statuses, kw, like, kw.toUpperCase(Locale.ROOT))
                .stream().map(support::summary).toList();
    }

    @Transactional(readOnly = true)
    public PublicIdResult byQr(String token, CurrentUser u) {
        LoanApplication app = apps.findByQrToken(token.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ theo mã QR."));
        if (app.getBranchId().intValue() != branchOf(u)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Hồ sơ thuộc chi nhánh khác.");
        }
        return new PublicIdResult(app.getPublicId());
    }

    @Transactional(readOnly = true)
    public StaffSummary summary(CurrentUser u) {
        Map<ApplicationStatus, Long> n = new HashMap<>();
        for (Object[] row : apps.countByStatus(branchOf(u))) {
            n.put((ApplicationStatus) row[0], (Long) row[1]);
        }
        return new StaffSummary(n.getOrDefault(ApplicationStatus.WAITING_VERIFY, 0L), n.getOrDefault(ApplicationStatus.NEED_MORE, 0L),
                n.getOrDefault(ApplicationStatus.VERIFIED, 0L), n.getOrDefault(ApplicationStatus.APPROVED, 0L));
    }

    // ---------------------------------------------------------------- 5.2 đối chiếu (MB09)

    @Transactional
    public Dtos.Application verify(String id, VerificationRequest b, CurrentUser u) {
        LoanApplication app = staffApp(id, u);
        if (app.getStatus() != ApplicationStatus.WAITING_VERIFY) {
            throw new ApiException(HttpStatus.CONFLICT, "Hồ sơ không ở trạng thái chờ đối chiếu.");
        }
        AppBundle bundle = support.load(app);
        List<VerificationRequest.Detail> details = b == null || b.details() == null ? List.of() : b.details();
        List<ApplicationDocument> docs = bundle.docs().stream()
                .filter(d -> d.getDocumentTypeId() != DocumentType.SYSTEM_DOC_TYPE && d.getFileName() != null).toList();
        if (docs.stream().anyMatch(d -> details.stream().noneMatch(x -> x != null && d.getId().equals(x.documentId())))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần ghi kết quả cho từng giấy tờ.");
        }
        if (details.stream().anyMatch(x -> x == null || (!Verification.MATCH.equals(x.result()) && blank(x.reason())))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần ghi lý do cho giấy tờ không khớp hoặc thiếu.");
        }
        // Chặt hơn mock: mỗi dòng phải ứng với đúng một giấy tờ đã tải, kết quả đúng giá trị, độ dài vừa cột.
        Map<String, String> bad = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < details.size(); i++) {
            VerificationRequest.Detail x = details.get(i);
            Long docId = x.documentId();
            if (docId == null || !seen.add(docId) || docs.stream().noneMatch(d -> d.getId().equals(docId))) {
                bad.put("details[" + i + "].documentId", "Giấy tờ không thuộc danh sách cần đối chiếu.");
            }
            if (!VERIFY_RESULTS.contains(x.result())) {
                bad.put("details[" + i + "].result", "Kết quả phải là MATCH, MISMATCH hoặc MISSING.");
            }
            if (x.reason() != null && x.reason().length() > 255) {
                bad.put("details[" + i + "].reason", "Tối đa 255 ký tự.");
            }
        }
        String note = b == null || b.note() == null ? "" : b.note();
        if (note.length() > 255) {
            bad.put("note", "Tối đa 255 ký tự.");
        }
        if (!bad.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_INVALID, bad);
        }

        boolean ok = details.stream().allMatch(x -> Verification.MATCH.equals(x.result()));
        OffsetDateTime now = now();
        Verification v = verifications.save(new Verification(app.getId(), (int) u.id(), ok ? Verification.MATCH : Verification.MISMATCH,
                note.isEmpty() ? null : note, now));
        List<String> noteParts = new ArrayList<>();
        List<String> bodyParts = new ArrayList<>();
        for (VerificationRequest.Detail x : details) {
            String reason = blank(x.reason()) ? null : x.reason();
            verificationDetails.save(new VerificationDetail(v.getId(), x.documentId(), x.result(), reason));
            ApplicationDocument d = docs.stream().filter(y -> y.getId().equals(x.documentId())).findFirst().orElseThrow();
            d.recordVerification(x.result(), reason);
            if (!ok && !Verification.MATCH.equals(x.result())) {
                noteParts.add(bundle.typeName(d) + ": " + x.reason());
                bodyParts.add(bundle.typeName(d) + " – " + x.reason());
            }
        }
        String link = "/ho-so/" + app.getPublicId();
        if (ok) {
            states.transition(app, ApplicationStatus.VERIFIED, support.actor(u), "Tất cả giấy tờ khớp bản gốc");
            notifications.toCustomer(app.getCustomerId(), "Hồ sơ đã đối chiếu xong",
                    "Hồ sơ " + app.getCode() + " đã được chi nhánh đối chiếu và chuyển ngân hàng thẩm định.", link);
        } else {
            states.transition(app, ApplicationStatus.NEED_MORE, support.actor(u), truncate(String.join("; ", noteParts), 255));
            notifications.toCustomer(app.getCustomerId(), "Hồ sơ cần bổ sung giấy tờ", truncate(String.join("; ", bodyParts), 500), link);
        }
        activity.log(u, "Đối chiếu giấy tờ", ok ? "Khớp toàn bộ" : "Có giấy tờ không khớp", app.getId());
        return support.view(support.load(app), u);
    }

    // ---------------------------------------------------------------- 5.3 kết quả xét duyệt (MB16)

    @Transactional
    public Dtos.Application decide(String id, DecisionRequest b, CurrentUser u) {
        LoanApplication app = staffApp(id, u);
        if (app.getStatus() != ApplicationStatus.VERIFIED) {
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ ghi nhận kết quả cho hồ sơ đã đối chiếu.");
        }
        if (b == null || !RESULT_TYPES.contains(b.resultType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_INVALID, Map.of("resultType", "Phải là APPROVED, REJECTED hoặc MORE_INFO."));
        }
        boolean approved = ApprovalResult.APPROVED.equals(b.resultType());
        if (!approved && blank(b.reason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần ghi lý do.");
        }
        if (approved && !(positive(b.approvedAmount()) && b.approvedTerm() != null && b.approvedTerm() > 0
                && b.approvedRate() != null && b.approvedRate().signum() > 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần ghi số tiền, kỳ hạn, lãi suất được duyệt.");
        }
        Map<String, String> bad = new LinkedHashMap<>();
        if (b.approvedAmount() != null && (b.approvedAmount() < 0 || b.approvedAmount() > MAX_AMOUNT)) {
            bad.put("approvedAmount", "Số tiền không hợp lệ.");
        }
        if (b.approvedTerm() != null && (b.approvedTerm() < 0 || b.approvedTerm() > 1200)) {
            bad.put("approvedTerm", "Kỳ hạn không hợp lệ.");
        }
        if (b.approvedRate() != null && (b.approvedRate().signum() < 0 || b.approvedRate().compareTo(MAX_RATE) > 0
                || b.approvedRate().stripTrailingZeros().scale() > 2)) {
            bad.put("approvedRate", "Lãi suất tối đa 999,99 và 2 chữ số thập phân.");
        }
        if (b.reason() != null && b.reason().length() > 255) {
            bad.put("reason", "Tối đa 255 ký tự.");
        }
        if (!bad.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_INVALID, bad);
        }

        approvals.save(new ApprovalResult(app.getId(), (int) u.id(), b.resultType(), b.approvedAmount(), b.approvedTerm(),
                b.approvedRate(), b.reason(), now()));
        String link = "/ho-so/" + app.getPublicId();
        ApplicationStatus to;
        if (approved) {
            to = ApplicationStatus.APPROVED;
            String money = JsFormat.vnd(b.approvedAmount());
            String rate = JsFormat.num(b.approvedRate().doubleValue());
            states.transition(app, to, support.actor(u), "Duyệt " + money + " đ, " + b.approvedTerm() + " tháng, " + rate + "%/năm");
            notifications.toCustomer(app.getCustomerId(), "Hồ sơ được phê duyệt", "Ngân hàng phê duyệt " + money + " đ, kỳ hạn "
                    + b.approvedTerm() + " tháng, lãi suất " + rate + "%/năm. Chi nhánh sẽ liên hệ ký hợp đồng.", link);
        } else if (ApprovalResult.REJECTED.equals(b.resultType())) {
            to = ApplicationStatus.REJECTED;
            states.transition(app, to, support.actor(u), b.reason());
            notifications.toCustomer(app.getCustomerId(), "Hồ sơ không được phê duyệt", truncate("Lý do: " + b.reason(), 500), link);
        } else {
            to = ApplicationStatus.NEED_MORE;
            states.transition(app, to, support.actor(u), truncate("Ngân hàng yêu cầu bổ sung: " + b.reason(), 255));
            notifications.toCustomer(app.getCustomerId(), "Ngân hàng yêu cầu bổ sung hồ sơ", b.reason(), link);
        }
        activity.log(u, "Cập nhật kết quả xét duyệt", to.label(), app.getId());
        return support.view(support.load(app), u);
    }

    // ---------------------------------------------------------------- 5.3 giải ngân

    @Transactional
    public Dtos.Application disburse(String id, DisbursementRequest b, CurrentUser u) {
        LoanApplication app = staffApp(id, u);
        ApprovalResult approval = approvals.findFirstByApplicationIdOrderByIdDesc(app.getId()).orElse(null);
        if (app.getStatus() != ApplicationStatus.APPROVED || approval == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ giải ngân hồ sơ đã được phê duyệt.");
        }
        if (b == null || blank(b.disbursedAt()) || !positive(b.disbursedAmount()) || blank(b.beneficiary())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần đủ ngày, số tiền giải ngân và bên thụ hưởng.");
        }
        long approvedAmount = approval.getApprovedAmount() == null ? 0 : approval.getApprovedAmount();
        if (b.disbursedAmount() > approvedAmount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Số tiền giải ngân vượt số tiền được duyệt.");
        }
        Map<String, String> bad = new LinkedHashMap<>();
        LocalDate at = null;
        try {
            at = LocalDate.parse(b.disbursedAt());
        } catch (DateTimeParseException e) {
            bad.put("disbursedAt", "Ngày phải có dạng yyyy-MM-dd.");
        }
        if (b.beneficiary().length() > 150) {
            bad.put("beneficiary", "Tối đa 150 ký tự.");
        }
        if (!bad.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_INVALID, bad);
        }

        approval.disburse(at, b.disbursedAmount(), b.beneficiary());
        String money = JsFormat.vnd(b.disbursedAmount());
        states.transition(app, ApplicationStatus.DISBURSED, support.actor(u), truncate("Giải ngân " + money + " đ cho " + b.beneficiary(), 255));
        notifications.toCustomer(app.getCustomerId(), "Khoản vay đã được giải ngân",
                money + " đ ngày " + at.format(DMY) + " vào " + b.beneficiary() + ".", "/ho-so/" + app.getPublicId());
        activity.log(u, "Cập nhật giải ngân", money + " đ", app.getId());
        return support.view(support.load(app), u);
    }

    // ---------------------------------------------------------------- nội bộ

    /** Mock {@code staffApp}: hồ sơ của chi nhánh nhân viên (khóa dòng đến hết giao dịch), ngược lại 404. */
    private LoanApplication staffApp(String id, CurrentUser u) {
        UUID publicId = PublicIds.parse(id, MSG_NOT_FOUND);
        return apps.findByPublicIdForUpdate(publicId)
                .filter(a -> a.getBranchId().intValue() == branchOf(u))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
    }

    private static int branchOf(CurrentUser u) {
        return u.branchId() == null ? -1 : u.branchId().intValue();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean positive(Long n) {
        return n != null && n > 0;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
