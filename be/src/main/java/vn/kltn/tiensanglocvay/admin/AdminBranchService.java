package vn.kltn.tiensanglocvay.admin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.admin.AdminDtos.AdminBranch;
import vn.kltn.tiensanglocvay.admin.AdminDtos.BranchDeactivateResult;
import vn.kltn.tiensanglocvay.admin.AdminDtos.BranchInput;
import vn.kltn.tiensanglocvay.admin.AdminDtos.BranchView;
import vn.kltn.tiensanglocvay.admin.AdminDtos.DeactivateBranchRequest;
import vn.kltn.tiensanglocvay.application.Actor;
import vn.kltn.tiensanglocvay.application.ApplicationStateMachine;
import vn.kltn.tiensanglocvay.application.ApplicationStatus;
import vn.kltn.tiensanglocvay.application.ApplicationSupport;
import vn.kltn.tiensanglocvay.application.LoanApplication;
import vn.kltn.tiensanglocvay.application.LoanApplicationRepository;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.auth.Staff;
import vn.kltn.tiensanglocvay.auth.StaffRepository;
import vn.kltn.tiensanglocvay.catalog.Branch;
import vn.kltn.tiensanglocvay.catalog.BranchRepository;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.notification.NotificationService;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * 1.6 Chi nhánh, chép từ fe/src/mocks/handlers/admin.ts. Đã chốt (23/10/2026): ngừng hoạt động chuyển cả nhân viên chi nhánh sang
 * chi nhánh nhận (mock gốc chỉ chuyển hồ sơ); còn hồ sơ đang xử lý hoặc còn nhân viên thì bắt buộc có chi nhánh nhận.
 */
@Service
public class AdminBranchService {

    static final String MSG_NOT_FOUND = "Không tìm thấy chi nhánh.";
    static final String MSG_DUP_CODE = "Mã chi nhánh đã tồn tại.";

    /** Hồ sơ "đang xử lý" của chi nhánh (openApplications, chuyển khi ngừng hoạt động). */
    static final Set<ApplicationStatus> OPEN = EnumSet.of(ApplicationStatus.DRAFTING, ApplicationStatus.WAITING_VERIFY,
            ApplicationStatus.NEED_MORE, ApplicationStatus.VERIFIED, ApplicationStatus.APPROVED);

    private final BranchRepository branches;
    private final LoanApplicationRepository apps;
    private final StaffRepository staff;
    private final ApplicationStateMachine states;
    private final ApplicationSupport support;
    private final NotificationService notifications;
    private final ActivityLogService activity;

    public AdminBranchService(BranchRepository branches, LoanApplicationRepository apps, StaffRepository staff,
                              ApplicationStateMachine states, ApplicationSupport support, NotificationService notifications,
                              ActivityLogService activity) {
        this.branches = branches;
        this.apps = apps;
        this.staff = staff;
        this.states = states;
        this.support = support;
        this.notifications = notifications;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public List<AdminBranch> list() {
        Map<Integer, Long> open = new HashMap<>();
        for (Object[] row : apps.countByBranch(OPEN)) {
            open.put((Integer) row[0], (Long) row[1]);
        }
        return branches.findAllByOrderById().stream().map(b -> new AdminBranch(b.getId(), b.getCode(), b.getName(), b.getAddress(),
                b.getPhone(), b.getStatus(), open.getOrDefault(b.getId(), 0L))).toList();
    }

    @Transactional
    public BranchView create(BranchInput b, CurrentUser u) {
        String code = b == null ? null : FieldErrors.trim(b.code());
        if (code != null && branches.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_DUP_CODE);
        }
        validate(b);
        Branch br;
        try {
            br = branches.saveAndFlush(new Branch(code, b.name().trim(), b.address().trim(), phone(b)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_DUP_CODE);
        }
        activity.log(u, "Thêm chi nhánh", br.getName(), null);
        return view(br);
    }

    @Transactional
    public BranchView update(int id, BranchInput b, CurrentUser u) {
        Branch br = branches.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
        String code = b == null ? null : FieldErrors.trim(b.code());
        if (code != null && branches.existsByCodeAndIdNot(code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_DUP_CODE);
        }
        validate(b);
        br.update(code, b.name().trim(), b.address().trim(), phone(b));
        activity.log(u, "Cập nhật chi nhánh", br.getName(), null);
        return view(br);
    }

    @Transactional
    public BranchDeactivateResult deactivate(int id, DeactivateBranchRequest b, CurrentUser u) {
        // Khóa dòng chi nhánh bị ngừng và chi nhánh nhận (FOR UPDATE) theo thứ tự id tăng dần, nên hai lần ngừng chéo nhau (A→B, B→A)
        // không khóa chết; POST /applications khóa cùng dòng branch, nên hồ sơ mới hoặc không vào chi nhánh này, hoặc đã có mặt khi
        // đếm hồ sơ đang xử lý bên dưới. Trạng thái ACTIVE của chi nhánh nhận được kiểm tra lại SAU khi khóa.
        Long toId = b == null ? null : b.transferToBranchId();
        Branch br = null;
        Branch target = null;
        for (int lockId : toId == null || toId == id ? new int[] {id} : new int[] {Math.min(id, toId.intValue()), Math.max(id, toId.intValue())}) {
            Branch locked = branches.findByIdForUpdate(lockId).orElse(null);
            if (lockId == id) {
                br = locked;
            } else {
                target = locked;
            }
        }
        if (br == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND);
        }
        List<LoanApplication> open = apps.findByBranchForUpdate(br.getId(), OPEN);
        List<Staff> people = staff.findByBranchIdAndRole(br.getId(), Staff.BRANCH_STAFF);
        Branch to = null;
        if (!open.isEmpty() || !people.isEmpty()) {
            to = target != null && target.isActive() ? target : null;
            if (to == null) {
                throw new ApiException(HttpStatus.CONFLICT, open.isEmpty()
                        ? "Chi nhánh còn " + people.size() + " nhân viên; chọn chi nhánh nhận chuyển."
                        : "Chi nhánh còn " + open.size() + " hồ sơ đang xử lý; chọn chi nhánh nhận chuyển.");
            }
            Actor actor = support.actor(u);
            for (LoanApplication a : open) {
                a.setBranchId(to.getId());
                states.note(a, actor, "Chuyển từ " + br.getName() + " sang " + to.getName());
                notifications.toCustomer(a.getCustomerId(), "Hồ sơ được chuyển chi nhánh",
                        "Hồ sơ " + a.getCode() + " được chuyển sang " + to.getName() + " (" + to.getAddress() + ").", "/ho-so/" + a.getPublicId());
                notifications.toBranch(to.getId(), "Nhận hồ sơ chuyển chi nhánh", a.getCode() + " từ " + br.getName(),
                        "/noi-bo/ho-so/" + a.getPublicId());
            }
            for (Staff s : people) {
                s.setBranchId(to.getId());
            }
        }
        br.setStatus(Branch.INACTIVE);
        activity.log(u, "Ngừng hoạt động chi nhánh", br.getName()
                + (open.isEmpty() ? "" : ", chuyển " + open.size() + " hồ sơ")
                + (people.isEmpty() ? "" : ", chuyển " + people.size() + " nhân viên"), null);
        return new BranchDeactivateResult(br.getId(), br.getCode(), br.getName(), br.getAddress(), br.getPhone(), br.getStatus(),
                open.size(), people.size());
    }

    @Transactional
    public BranchView activate(int id, CurrentUser u) {
        Branch br = branches.findByIdForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
        br.setStatus(Branch.ACTIVE);
        activity.log(u, "Mở lại chi nhánh", br.getName(), null);
        return view(br);
    }

    /** Chặt hơn mock: trường bắt buộc và độ dài theo cột của bảng branch. */
    private static void validate(BranchInput b) {
        FieldErrors e = new FieldErrors();
        if (b == null) {
            e.add("code", "Không được để trống.");
        } else {
            e.text("code", b.code(), 20);
            e.text("name", b.name(), 150);
            e.text("address", b.address(), 255);
            e.optionalText("phone", b.phone(), 15);
        }
        e.throwIfAny();
    }

    private static String phone(BranchInput b) {
        return b.phone() == null || b.phone().isBlank() ? null : b.phone().trim();
    }

    private static BranchView view(Branch b) {
        return new BranchView(b.getId(), b.getCode(), b.getName(), b.getAddress(), b.getPhone(), b.getStatus());
    }
}
