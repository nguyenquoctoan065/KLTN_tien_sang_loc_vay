package vn.kltn.tiensanglocvay.admin;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.admin.AdminDtos.CreateStaffRequest;
import vn.kltn.tiensanglocvay.admin.AdminDtos.StaffAccount;
import vn.kltn.tiensanglocvay.admin.AdminDtos.UpdateStaffRequest;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.auth.SessionService;
import vn.kltn.tiensanglocvay.auth.Staff;
import vn.kltn.tiensanglocvay.auth.StaffRepository;
import vn.kltn.tiensanglocvay.catalog.Branch;
import vn.kltn.tiensanglocvay.catalog.BranchRepository;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.security.AccountKind;
import vn.kltn.tiensanglocvay.security.CurrentUser;

/**
 * 1.5 Tài khoản nội bộ, chép từ fe/src/mocks/handlers/admin.ts. Khác mock có chủ đích: mật khẩu tạm ngẫu nhiên 10 ký tự (mock: 123456),
 * khóa tài khoản và đặt lại mật khẩu đều thu hồi mọi phiên của nhân viên đó.
 */
@Service
public class AdminStaffService {

    static final String MSG_NOT_FOUND = "Không tìm thấy tài khoản.";
    static final String MSG_EMAIL = "Email không hợp lệ.";
    static final String MSG_EMAIL_USED = "Email đã được dùng.";
    static final String MSG_NEED_BRANCH = "Nhân viên chi nhánh cần chọn chi nhánh.";
    static final String MSG_SELF_DEMOTE = "Không tự hạ quyền của chính mình.";
    static final String MSG_SELF_LOCK = "Không tự khóa tài khoản của mình.";
    static final String MSG_BAD_ACTION = "Thao tác không hợp lệ.";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> ROLES = Set.of(Staff.BRANCH_STAFF, Staff.ADMIN);
    /** Bỏ các ký tự dễ nhầm (0/O, 1/l/I) vì mật khẩu tạm được đọc và gõ lại bằng tay. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaffRepository staff;
    private final BranchRepository branches;
    private final SessionService sessions;
    private final PasswordEncoder encoder;
    private final ActivityLogService activity;
    private final Clock clock;

    public AdminStaffService(StaffRepository staff, BranchRepository branches, SessionService sessions, PasswordEncoder encoder,
                             ActivityLogService activity, Clock clock) {
        this.staff = staff;
        this.branches = branches;
        this.sessions = sessions;
        this.encoder = encoder;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StaffAccount> list() {
        Map<Integer, String> names = branchNames();
        return staff.findAllByOrderById().stream().map(s -> out(s, names, null)).toList();
    }

    @Transactional
    public StaffAccount create(CreateStaffRequest b, CurrentUser u) {
        if (b == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FieldErrors.INVALID);
        }
        String email = FieldErrors.trim(b.email());
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_EMAIL);
        }
        if (staff.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_EMAIL_USED);
        }
        if (Staff.BRANCH_STAFF.equals(b.role()) && b.branchId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_NEED_BRANCH);
        }
        Integer branchId = checkRoleAndBranch(b.fullName(), b.role(), b.branchId(), email);
        String temp = tempPassword();
        Staff s;
        try {
            s = staff.saveAndFlush(new Staff(b.fullName().trim(), email, encoder.encode(temp), b.role(), branchId, OffsetDateTime.now(clock)));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_EMAIL_USED); // hai yêu cầu tạo cùng email chạy song song
        }
        activity.log(u, "Tạo tài khoản nhân viên", email, null);
        return out(s, branchNames(), temp);
    }

    @Transactional
    public StaffAccount update(int id, UpdateStaffRequest b, CurrentUser u) {
        Staff s = staff.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
        if (b == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FieldErrors.INVALID);
        }
        if (Staff.BRANCH_STAFF.equals(b.role()) && b.branchId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, MSG_NEED_BRANCH);
        }
        if (s.getId() == u.id() && !Staff.ADMIN.equals(b.role())) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_SELF_DEMOTE);
        }
        Integer branchId = checkRoleAndBranch(b.fullName(), b.role(), b.branchId(), s.getEmail());
        s.setFullName(b.fullName().trim());
        s.setRole(b.role());
        s.setBranchId(branchId);
        activity.log(u, "Cập nhật tài khoản nhân viên", s.getEmail(), null);
        return out(s, branchNames(), null);
    }

    /** lock / unlock / reset-password; thao tác lạ → 404 "Thao tác không hợp lệ." như mock (sau khi tìm tài khoản). */
    @Transactional
    public StaffAccount act(int id, String action, CurrentUser u) {
        Staff s = staff.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, MSG_NOT_FOUND));
        if (s.getId() == u.id() && "lock".equals(action)) {
            throw new ApiException(HttpStatus.CONFLICT, MSG_SELF_LOCK);
        }
        String temp = null;
        String label;
        switch (action) {
            case "lock" -> {
                s.setStatus("LOCKED");
                sessions.revokeAll(AccountKind.STAFF, s.getId(), null);
                label = "Khóa";
            }
            case "unlock" -> {
                s.setStatus("ACTIVE");
                label = "Mở khóa";
            }
            case "reset-password" -> {
                temp = tempPassword();
                s.setPasswordHash(encoder.encode(temp));
                sessions.revokeAll(AccountKind.STAFF, s.getId(), null);
                label = "Đặt lại mật khẩu";
            }
            default -> throw new ApiException(HttpStatus.NOT_FOUND, MSG_BAD_ACTION);
        }
        activity.log(u, "Quản lý tài khoản nhân viên", label + " " + s.getEmail(), null);
        return out(s, branchNames(), temp);
    }

    /** Kiểm tra chặt hơn mock (400 kèm {@code fields}); trả {@code branchId} sẽ lưu (quản trị viên luôn {@code null}). */
    private Integer checkRoleAndBranch(String fullName, String role, Long branchId, String email) {
        FieldErrors e = new FieldErrors();
        e.text("fullName", fullName, 100);
        e.oneOf("role", role, ROLES);
        e.optionalText("email", email, 100);
        if (Staff.BRANCH_STAFF.equals(role) && branchId != null
                && branches.findById(branchId.intValue()).filter(Branch::isActive).isEmpty()) {
            e.add("branchId", "Chi nhánh không tồn tại hoặc đã ngừng hoạt động.");
        }
        e.throwIfAny();
        return Staff.ADMIN.equals(role) ? null : branchId.intValue();
    }

    private Map<Integer, String> branchNames() {
        return branches.findAll().stream().collect(Collectors.toMap(Branch::getId, Branch::getName));
    }

    private static StaffAccount out(Staff s, Map<Integer, String> branchNames, String tempPassword) {
        return new StaffAccount(s.getId(), s.getFullName(), s.getEmail(), s.getRole(),
                s.getBranchId() == null ? null : s.getBranchId().longValue(),
                s.getBranchId() == null ? null : branchNames.get(s.getBranchId()), s.getStatus(), s.getCreatedAt(), tempPassword);
    }

    private static String tempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
