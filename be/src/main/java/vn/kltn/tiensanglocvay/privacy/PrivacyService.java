package vn.kltn.tiensanglocvay.privacy;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.application.ApplicationDocument;
import vn.kltn.tiensanglocvay.application.ApplicationDocumentRepository;
import vn.kltn.tiensanglocvay.application.ApplicationStateMachine;
import vn.kltn.tiensanglocvay.application.ApplicationStatus;
import vn.kltn.tiensanglocvay.application.ApplicationSupport;
import vn.kltn.tiensanglocvay.application.ExtractedFieldRepository;
import vn.kltn.tiensanglocvay.application.LoanApplication;
import vn.kltn.tiensanglocvay.application.LoanApplicationRepository;
import vn.kltn.tiensanglocvay.application.LoanRequestFormRepository;
import vn.kltn.tiensanglocvay.audit.ActivityLog;
import vn.kltn.tiensanglocvay.audit.ActivityLogDto;
import vn.kltn.tiensanglocvay.audit.ActivityLogRepository;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.auth.Customer;
import vn.kltn.tiensanglocvay.auth.CustomerRepository;
import vn.kltn.tiensanglocvay.auth.OtpService;
import vn.kltn.tiensanglocvay.auth.Staff;
import vn.kltn.tiensanglocvay.auth.StaffRepository;
import vn.kltn.tiensanglocvay.catalog.DocumentType;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.document.StorageService;
import vn.kltn.tiensanglocvay.document.StoredFile;
import vn.kltn.tiensanglocvay.notification.NotificationService;
import vn.kltn.tiensanglocvay.plan.LoanPlanRepository;
import vn.kltn.tiensanglocvay.privacy.PrivacyDtos.Account;
import vn.kltn.tiensanglocvay.privacy.PrivacyDtos.ConsentRecord;
import vn.kltn.tiensanglocvay.privacy.PrivacyDtos.MyApplication;
import vn.kltn.tiensanglocvay.privacy.PrivacyDtos.MyData;
import vn.kltn.tiensanglocvay.privacy.PrivacyDtos.WithdrawConsentRequest;
import vn.kltn.tiensanglocvay.privacy.PrivacyDtos.WithdrawConsentResult;
import vn.kltn.tiensanglocvay.screening.Consent;
import vn.kltn.tiensanglocvay.screening.ConsentRepository;
import vn.kltn.tiensanglocvay.screening.ScreeningRepository;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * 6.1 xem dữ liệu cá nhân và 6.3 rút đồng ý (docs/quy-tac-tinh-toan.md mục 14), chép từ fe/src/mocks/handlers/me.ts.
 * Quyền khách hàng đã được CustomerOnlyFilter kiểm tra (401 "Cần đăng nhập tài khoản khách hàng.").
 */
@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);

    static final String MSG_LOGIN = "Cần đăng nhập tài khoản khách hàng.";
    static final String MSG_STORAGE = "Không xóa được tệp trên kho lưu trữ, vui lòng thử lại sau.";
    private static final int LOG_LIMIT = 100;
    /** Hồ sơ rút đồng ý phần (b) xử lý: hủy (nếu chưa) và xóa dữ liệu; các trạng thái còn lại được giữ (mock). */
    private static final Set<ApplicationStatus> WITHDRAWABLE = EnumSet.of(ApplicationStatus.DRAFTING, ApplicationStatus.WAITING_VERIFY,
            ApplicationStatus.NEED_MORE, ApplicationStatus.EXPIRED, ApplicationStatus.CANCELLED);

    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final LoanApplicationRepository apps;
    private final ApplicationDocumentRepository docs;
    private final ExtractedFieldRepository fields;
    private final LoanRequestFormRepository forms;
    private final ConsentRepository consents;
    private final ScreeningRepository screenings;
    private final LoanPlanRepository plans;
    private final ActivityLogRepository logs;
    private final ActivityLogService activity;
    private final NotificationService notifications;
    private final ApplicationStateMachine states;
    private final ApplicationSupport support;
    private final OtpService otp;
    private final StorageService storage;
    private final EntityManager em;
    private final Clock clock;

    public PrivacyService(CustomerRepository customers, StaffRepository staff, LoanApplicationRepository apps,
                          ApplicationDocumentRepository docs, ExtractedFieldRepository fields, LoanRequestFormRepository forms,
                          ConsentRepository consents, ScreeningRepository screenings, LoanPlanRepository plans, ActivityLogRepository logs,
                          ActivityLogService activity, NotificationService notifications, ApplicationStateMachine states,
                          ApplicationSupport support, OtpService otp, StorageService storage, EntityManager em, Clock clock) {
        this.customers = customers;
        this.staff = staff;
        this.apps = apps;
        this.docs = docs;
        this.fields = fields;
        this.forms = forms;
        this.consents = consents;
        this.screenings = screenings;
        this.plans = plans;
        this.logs = logs;
        this.activity = activity;
        this.notifications = notifications;
        this.states = states;
        this.support = support;
        this.otp = otp;
        this.storage = storage;
        this.em = em;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 6.1 xem dữ liệu

    @Transactional
    public MyData myData(CurrentUser u) {
        Customer c = customer(u);
        List<LoanApplication> mine = apps.findByCustomerIdOrderByIdAsc(c.getId());
        // Hồ sơ mới nhất có họ tên (mock: apps.reverse().find(a => a.personal.fullName)).
        LoanApplication latest = null;
        for (int i = mine.size() - 1; i >= 0 && latest == null; i--) {
            String name = mine.get(i).getFullName();
            if (name != null && !name.isEmpty()) {
                latest = mine.get(i);
            }
        }
        Map<Long, String> codeByConsent = new HashMap<>();
        for (LoanApplication a : mine) {
            codeByConsent.put(a.getConsentId(), a.getCode());
        }
        List<ConsentRecord> consentList = consents.findByCustomerIdOrderByIdDesc(c.getId()).stream()
                .map(k -> new ConsentRecord(k.getId(), k.getScope(), k.getDocVersion(), k.getAgreedAt(), k.getWithdrawnAt(),
                        codeByConsent.get(k.getId())))
                .toList();
        Map<Long, Integer> fileCount = new HashMap<>();
        if (!mine.isEmpty()) {
            for (Object[] row : docs.countFiles(mine.stream().map(LoanApplication::getId).toList(), DocumentType.SYSTEM_DOC_TYPE)) {
                fileCount.put((Long) row[0], ((Long) row[1]).intValue());
            }
        }
        List<MyApplication> applicationList = mine.stream()
                .map(a -> new MyApplication(a.getCode(), a.getStatus(), fileCount.getOrDefault(a.getId(), 0))).toList();
        MyData out = new MyData(new Account(c.getPhone(), ApplicationSupport.customerName(c), c.getCreatedAt()),
                latest == null ? null : ApplicationSupport.personal(latest, c.getPhone()), consentList,
                screenings.countByCustomerId(c.getId()), plans.countByCustomer(c.getId()), applicationList,
                accessLog(c, mine));
        // Lượt xem được ghi sau khi dựng kết quả nên không nằm trong chính nhật ký vừa trả (như mock).
        activity.log(u, "Xem dữ liệu cá nhân", "", null);
        return out;
    }

    /** 100 dòng nhật ký gần nhất liên quan tới khách: thao tác của khách và mọi thao tác trên hồ sơ của khách. */
    private List<ActivityLogDto> accessLog(Customer c, List<LoanApplication> mine) {
        List<ActivityLog> rows = logs.findForCustomer(c.getId(), PageRequest.of(0, LOG_LIMIT));
        Map<Long, String> codes = mine.stream().collect(Collectors.toMap(LoanApplication::getId, LoanApplication::getCode));
        Map<Long, String> staffNames = staff.findAllById(rows.stream().filter(l -> "STAFF".equals(l.getActorType()) && l.getActorId() != null)
                .map(l -> l.getActorId().intValue()).distinct().toList()).stream()
                .collect(Collectors.toMap(s -> s.getId().longValue(), Staff::getFullName));
        String customerName = ApplicationSupport.customerName(c);
        return rows.stream().map(l -> new ActivityLogDto(l.getId(), l.getApplicationId() == null ? null : codes.get(l.getApplicationId()),
                l.getActorType(), actorName(l, customerName, staffNames), l.getAction(), l.getDetail() == null ? "" : l.getDetail(),
                l.getCreatedAt())).toList();
    }

    private static String actorName(ActivityLog l, String customerName, Map<Long, String> staffNames) {
        return switch (l.getActorType()) {
            case "CUSTOMER" -> customerName;
            case "STAFF" -> staffNames.getOrDefault(l.getActorId(), "Nhân viên");
            default -> "Hệ thống";
        };
    }

    // ---------------------------------------------------------------- 6.3 rút đồng ý

    @Transactional
    public WithdrawConsentResult withdraw(WithdrawConsentRequest b, CurrentUser u) {
        String scope = b == null ? null : b.scope();
        if (!"A".equals(scope) && !"B".equals(scope)) {
            // Kiểm tra trước OTP để yêu cầu sai không làm tăng số lần nhập sai của mã.
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ.", Map.of("scope", "Phải là A hoặc B."));
        }
        Customer c = customer(u);
        long otpId = otp.check(c.getPhone(), OtpService.PURPOSE_CONFIRM, b.otp());
        OffsetDateTime at = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
        WithdrawConsentResult res = "A".equals(scope) ? withdrawA(c, at) : withdrawB(c, u, at);
        activity.log(u, "Rút lại sự đồng ý", "Phần (" + scope.toLowerCase() + ")", null);
        otp.consume(otpId);
        return res;
    }

    /** Phần (a): xóa lượt sàng lọc đã lưu và phương án không gắn hồ sơ vay; đánh dấu rút đồng ý phần A. */
    private WithdrawConsentResult withdrawA(Customer c, OffsetDateTime at) {
        List<Long> ids = screenings.findUnlinkedIds(c.getId());
        if (!ids.isEmpty()) {
            em.createQuery("delete from PlanCrossSell x where x.planId in (select p.id from LoanPlan p where p.screeningId in :ids)")
                    .setParameter("ids", ids).executeUpdate();
            em.createQuery("delete from LoanPlan p where p.screeningId in :ids").setParameter("ids", ids).executeUpdate();
            em.createQuery("delete from OtherDebt d where d.screeningId in :ids").setParameter("ids", ids).executeUpdate();
            em.createQuery("delete from ScreeningResult r where r.screeningId in :ids").setParameter("ids", ids).executeUpdate();
            em.createQuery("delete from Screening s where s.id in :ids").setParameter("ids", ids).executeUpdate();
        }
        markWithdrawn(c, "A", at);
        return new WithdrawConsentResult("A", ids.size(), 0, List.of(), List.of(),
                "Đã xóa " + ids.size() + " lượt sàng lọc và phương án liên quan. Lượt sàng lọc gắn với hồ sơ vay được giữ theo đồng ý phần (b).");
    }

    /**
     * Phần (b): hồ sơ DRAFTING / WAITING_VERIFY / NEED_MORE / EXPIRED chuyển CANCELLED, mọi hồ sơ trong nhóm (kể cả đã hủy trước đó) bị xóa
     * tệp giấy tờ, trường trích xuất, giấy đề nghị, CCCD, địa chỉ, email, cảnh báo; hồ sơ đã đối chiếu trở đi được giữ.
     * Tệp trong kho xóa **trong giao dịch**: không xóa được thì hoàn tác toàn bộ (502), khách thử lại; xóa tệp không còn là thao tác lặp được.
     */
    private WithdrawConsentResult withdrawB(Customer c, CurrentUser u, OffsetDateTime at) {
        List<String> cancelled = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        List<StoredFile> toDelete = new ArrayList<>();
        int deletedDocuments = 0;
        var actor = support.actor(u);
        for (LoanApplication a : apps.findByCustomerIdForUpdate(c.getId())) {
            if (!WITHDRAWABLE.contains(a.getStatus())) {
                kept.add(a.getCode());
                continue;
            }
            if (a.getStatus() != ApplicationStatus.CANCELLED) {
                states.transition(a, ApplicationStatus.CANCELLED, actor, "Khách hàng rút lại sự đồng ý");
                a.setCancelReason("Khách hàng rút lại sự đồng ý");
                cancelled.add(a.getCode());
                notifications.toBranch(a.getBranchId(), "Hồ sơ đã hủy do khách hàng rút đồng ý", a.getCode(),
                        "/noi-bo/ho-so/" + a.getPublicId());
            }
            for (ApplicationDocument d : docs.findByApplicationIdOrderById(a.getId())) {
                if (d.getFileName() != null && d.getDocumentTypeId() != DocumentType.SYSTEM_DOC_TYPE) {
                    deletedDocuments++;
                }
                StoredFile stored = d.stored();
                if (stored != null) {
                    toDelete.add(stored);
                }
                d.clearFile();
                fields.deleteByDocument(d.getId());
            }
            forms.findByApplicationId(a.getId()).ifPresent(forms::delete);
            a.setNationalId(null);
            a.setAddress(null);
            a.setEmail(null);
            a.setChecks(List.of(), null);
        }
        for (StoredFile f : toDelete) {
            try {
                storage.delete(f);
            } catch (RuntimeException e) {
                log.error("Không xóa được tệp {} khi khách hàng rút đồng ý: {}", f.publicId(), e.toString());
                throw new ApiException(HttpStatus.BAD_GATEWAY, MSG_STORAGE);
            }
        }
        markWithdrawn(c, "B", at);
        String message = "Đã xóa " + deletedDocuments + " tài liệu và thông tin định danh của hồ sơ chưa đối chiếu xong."
                + (kept.isEmpty() ? "" : " Hồ sơ đã đối chiếu tại chi nhánh được ngân hàng lưu trữ theo quy định; liên hệ chi nhánh nếu cần hỗ trợ.");
        return new WithdrawConsentResult("B", 0, deletedDocuments, cancelled, kept, message);
    }

    private void markWithdrawn(Customer c, String scope, OffsetDateTime at) {
        for (Consent k : consents.findByCustomerIdAndScopeAndWithdrawnAtIsNull(c.getId(), scope)) {
            k.withdraw(at);
        }
    }

    private Customer customer(CurrentUser u) {
        return customers.findById(u.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, MSG_LOGIN));
    }
}
