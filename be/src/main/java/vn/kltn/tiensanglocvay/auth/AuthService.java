package vn.kltn.tiensanglocvay.auth;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.audit.ActivityLogService;
import vn.kltn.tiensanglocvay.common.ApiException;
import vn.kltn.tiensanglocvay.security.AccountKind;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.Role;

/** 1.1–1.4: đăng nhập, đăng ký, thông tin tài khoản, đổi mật khẩu. Thông báo lỗi lấy đúng chữ trong fe/src/mocks/handlers/auth.ts. */
@Service
public class AuthService {

    private static final Pattern PHONE = Pattern.compile("^0\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final SessionService sessions;
    private final OtpService otp;
    private final ActivityLogService activity;
    private final PasswordEncoder encoder;
    private final Clock clock;
    /** Băm giả để thời gian phản hồi không lộ tài khoản có tồn tại hay không. */
    private final String dummyHash;

    public AuthService(CustomerRepository customers, StaffRepository staff, SessionService sessions, OtpService otp,
                       ActivityLogService activity, PasswordEncoder encoder, Clock clock) {
        this.customers = customers;
        this.staff = staff;
        this.sessions = sessions;
        this.otp = otp;
        this.activity = activity;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = encoder.encode("khong-ton-tai");
    }

    /** Dữ liệu chung của hai loại tài khoản. */
    private record Account(AccountKind kind, long id, String name, Role role, String username, String email, Integer branchId,
                           String passwordHash, boolean active) {

        CurrentUser current() {
            return new CurrentUser(kind, id, role, branchId == null ? null : branchId.longValue());
        }
    }

    private static Account of(Customer c) {
        String name = c.getFullName() != null ? c.getFullName() : "Khách hàng " + last4(c.getPhone());
        return new Account(AccountKind.CUSTOMER, c.getId(), name, Role.CUSTOMER, c.getPhone(), c.getEmail(), null,
                c.getPasswordHash(), c.isActive());
    }

    private static Account of(Staff s) {
        return new Account(AccountKind.STAFF, s.getId(), s.getFullName(), Role.valueOf(s.getRole()), s.getEmail(), null,
                s.getBranchId(), s.getPasswordHash(), s.isActive());
    }

    private static String last4(String phone) {
        return phone.substring(Math.max(0, phone.length() - 4));
    }

    private Optional<Account> find(AccountKind kind, long id) {
        return kind == AccountKind.CUSTOMER
                ? customers.findById(id).map(AuthService::of)
                : staff.findById((int) id).map(AuthService::of);
    }

    private AuthUser toUser(Account a) {
        String branchName = a.branchId() == null ? null : staff.findBranchName(a.branchId()).orElse(null);
        return new AuthUser(a.id(), a.name(), a.role(), a.username(), a.email(), branchName);
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        String u = req.username().trim();
        Optional<Account> found;
        if (req.kind() == AccountKind.CUSTOMER) {
            found = (u.contains("@") ? customers.findByEmailIgnoreCase(u) : customers.findByPhone(u)).map(AuthService::of);
        } else {
            found = staff.findByEmail(u).map(AuthService::of);
        }
        boolean matches = encoder.matches(req.password(), found.map(Account::passwordHash).orElse(dummyHash));
        if (found.isEmpty() || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu.");
        }
        Account acc = found.get();
        if (!acc.active()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa, liên hệ quản trị viên.");
        }
        String token = sessions.create(acc.kind(), acc.id());
        activity.log(acc.current(), "Đăng nhập", acc.kind() == AccountKind.CUSTOMER ? "Khách hàng đăng nhập" : "Nhân viên đăng nhập", null);
        return new LoginResponse(token, toUser(acc));
    }

    /** Luôn thành công; chỉ ghi nhật ký khi token còn hiệu lực. */
    @Transactional
    public void logout(String token, CurrentUser user) {
        if (user != null) {
            activity.log(user, "Đăng xuất", "", null);
        }
        if (token != null) {
            sessions.revoke(token);
        }
    }

    @Transactional(readOnly = true)
    public AuthUser me(CurrentUser user) {
        Account acc = find(user.kind(), user.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập."));
        return toUser(acc);
    }

    /** Gửi OTP: chưa đăng nhập thì tới {@code phone} (đăng ký); khách hàng đang đăng nhập thì tới số của tài khoản. */
    public OtpSent sendOtp(String phone, CurrentUser user) {
        boolean customer = user != null && user.role() == Role.CUSTOMER;
        String target = phone != null && !phone.isEmpty() ? phone
                : customer ? customers.findById(user.id()).map(Customer::getPhone).orElse("") : "";
        if (!PHONE.matcher(target).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ.");
        }
        return otp.issue(target, customer ? OtpService.PURPOSE_CONFIRM : OtpService.PURPOSE_REGISTER);
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        String email = req.email() == null || req.email().isBlank() ? null : req.email().trim();
        if (email != null && !EMAIL.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ.", Map.of("email", "Email không hợp lệ."));
        }
        long otpId = otp.check(req.phone(), OtpService.PURPOSE_REGISTER, req.otp());
        if (customers.existsByPhone(req.phone())) {
            throw new ApiException(HttpStatus.CONFLICT, "Số điện thoại đã được đăng ký.");
        }
        if (email != null && customers.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        Customer c;
        try {
            c = customers.saveAndFlush(new Customer(req.phone(), email, encoder.encode(req.password()),
                    "Khách hàng " + last4(req.phone()), OffsetDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Hai yêu cầu đăng ký đồng thời vượt qua bước kiểm tra trùng ở trên.
            boolean byEmail = String.valueOf(e.getMessage()).contains("ux_customer_email_lower");
            throw new ApiException(HttpStatus.CONFLICT, byEmail ? "Email đã được sử dụng." : "Số điện thoại đã được đăng ký.");
        }
        otp.consume(otpId);
        Account acc = of(c);
        String token = sessions.create(acc.kind(), acc.id());
        activity.log(acc.current(), "Đăng ký tài khoản", "", null);
        return new LoginResponse(token, toUser(acc));
    }

    /** Đổi mật khẩu và thu hồi các phiên khác của tài khoản (phiên đang dùng được giữ lại như mock). */
    @Transactional
    public void changePassword(CurrentUser user, String currentToken, ChangePasswordRequest req) {
        String oldPassword = req.oldPassword();
        String newPassword = req.newPassword();
        Account acc = find(user.kind(), user.id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập."));
        if (oldPassword == null || !encoder.matches(oldPassword, acc.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu mới tối thiểu 6 ký tự.");
        }
        if (newPassword.equals(oldPassword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        String hash = encoder.encode(newPassword);
        if (user.kind() == AccountKind.CUSTOMER) {
            customers.findById(user.id()).orElseThrow().setPasswordHash(hash);
        } else {
            staff.findById((int) user.id()).orElseThrow().setPasswordHash(hash);
        }
        sessions.revokeAll(user.kind(), user.id(), currentToken);
        activity.log(user, "Đổi mật khẩu", "", null);
    }
}
