package vn.kltn.tiensanglocvay.notification;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByCustomerIdOrderByIdDesc(Long customerId, Limit limit);

    List<Notification> findByCustomerIdAndReadAtIsNull(Long customerId);

    /** Thông báo gửi chi nhánh (mock: {@code branchId === u.branchId && customerId === null}). */
    List<Notification> findByBranchIdAndCustomerIdIsNullOrderByIdDesc(Integer branchId, Limit limit);

    List<Notification> findByBranchIdAndCustomerIdIsNullAndReadAtIsNull(Integer branchId);
}
