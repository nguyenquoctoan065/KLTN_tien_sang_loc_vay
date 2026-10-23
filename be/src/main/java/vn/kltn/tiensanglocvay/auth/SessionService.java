package vn.kltn.tiensanglocvay.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.common.Hashing;
import vn.kltn.tiensanglocvay.security.AccountKind;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.Role;

/** Phiên đăng nhập (docs/be-huong-dan.md mục 5.1): token ngẫu nhiên 32 byte, DB chỉ giữ SHA-256. */
@Service
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthSessionRepository sessions;
    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final Clock clock;
    private final long sessionHours;

    public SessionService(AuthSessionRepository sessions, CustomerRepository customers, StaffRepository staff, Clock clock,
                          @Value("${app.session-hours:8}") long sessionHours) {
        this.sessions = sessions;
        this.customers = customers;
        this.staff = staff;
        this.clock = clock;
        this.sessionHours = sessionHours;
    }

    /** Tạo phiên mới, trả token gốc (chỉ trả một lần cho FE). */
    @Transactional
    public String create(AccountKind kind, long accountId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime now = OffsetDateTime.now(clock);
        sessions.save(new AuthSession(Hashing.sha256Hex(token), kind, accountId, now, now.plusHours(sessionHours)));
        return token;
    }

    /** Người dùng của token; rỗng nếu token không có, đã thu hồi, hết hạn hoặc tài khoản không còn ACTIVE. */
    @Transactional(readOnly = true)
    public Optional<CurrentUser> authenticate(String token) {
        return sessions.findActive(Hashing.sha256Hex(token), OffsetDateTime.now(clock)).flatMap(s -> switch (s.getAccountKind()) {
            case CUSTOMER -> customers.findById(s.getAccountId()).filter(Customer::isActive)
                    .map(c -> new CurrentUser(AccountKind.CUSTOMER, c.getId(), Role.CUSTOMER, null));
            case STAFF -> staff.findById((int) s.getAccountId()).filter(Staff::isActive)
                    .map(a -> new CurrentUser(AccountKind.STAFF, a.getId(), Role.valueOf(a.getRole()),
                            a.getBranchId() == null ? null : a.getBranchId().longValue()));
        });
    }

    @Transactional
    public void revoke(String token) {
        sessions.revoke(Hashing.sha256Hex(token), OffsetDateTime.now(clock));
    }

    /** Thu hồi mọi phiên còn hiệu lực của tài khoản, trừ phiên của {@code keepToken} (null để thu hồi hết). */
    @Transactional
    public void revokeAll(AccountKind kind, long accountId, String keepToken) {
        String keepHash = keepToken == null ? "" : Hashing.sha256Hex(keepToken);
        sessions.revokeAll(kind, accountId, keepHash, OffsetDateTime.now(clock));
    }
}
