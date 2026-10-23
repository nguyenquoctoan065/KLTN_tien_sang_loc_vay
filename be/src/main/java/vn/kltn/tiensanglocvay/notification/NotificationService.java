package vn.kltn.tiensanglocvay.notification;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.kltn.tiensanglocvay.security.CurrentUser;
import vn.kltn.tiensanglocvay.security.Role;

/** Tương đương {@code notify(...)} trong fe/src/mocks/ctx.ts và các handler thông báo trong handlers/me.ts. */
@Service
public class NotificationService {

    private static final int MAX_LIST = 50;

    private final NotificationRepository repo;
    private final Clock clock;

    public NotificationService(NotificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Transactional
    public void toCustomer(long customerId, String title, String body, String link) {
        repo.save(new Notification(customerId, null, title, body, link, OffsetDateTime.now(clock)));
    }

    @Transactional
    public void toBranch(int branchId, String title, String body, String link) {
        repo.save(new Notification(null, branchId, title, body, link, OffsetDateTime.now(clock)));
    }

    /** Khách: của mình; nhân viên: của chi nhánh; quản trị: rỗng. Tối đa 50, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<AppNotification> list(CurrentUser u) {
        return mine(u, true).stream().map(AppNotification::of).toList();
    }

    /** Thông báo không thuộc người gọi bị bỏ qua. */
    @Transactional
    public void read(long id, CurrentUser u) {
        repo.findById(id).filter(n -> owns(n, u)).ifPresent(n -> n.markRead(OffsetDateTime.now(clock)));
    }

    @Transactional
    public void readAll(CurrentUser u) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        mine(u, false).forEach(n -> n.markRead(now));
    }

    private List<Notification> mine(CurrentUser u, boolean forList) {
        if (u.role() == Role.CUSTOMER) {
            return forList ? repo.findByCustomerIdOrderByIdDesc(u.id(), Limit.of(MAX_LIST)) : repo.findByCustomerIdAndReadAtIsNull(u.id());
        }
        if (u.role() == Role.BRANCH_STAFF && u.branchId() != null) {
            int b = u.branchId().intValue();
            return forList ? repo.findByBranchIdAndCustomerIdIsNullOrderByIdDesc(b, Limit.of(MAX_LIST))
                    : repo.findByBranchIdAndCustomerIdIsNullAndReadAtIsNull(b);
        }
        return List.of();
    }

    private static boolean owns(Notification n, CurrentUser u) {
        if (u.role() == Role.CUSTOMER) {
            return n.getCustomerId() != null && n.getCustomerId() == u.id();
        }
        return u.role() == Role.BRANCH_STAFF && n.getCustomerId() == null && u.branchId() != null
                && n.getBranchId() != null && n.getBranchId().longValue() == u.branchId();
    }
}
