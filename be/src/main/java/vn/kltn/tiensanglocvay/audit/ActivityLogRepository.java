package vn.kltn.tiensanglocvay.audit;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Nhật ký liên quan tới khách hàng (GET /me/data): thao tác của chính khách và mọi thao tác trên hồ sơ của khách
     * (kể cả nhân viên xem hồ sơ), mới nhất trước.
     */
    @Query("select l from ActivityLog l where (l.actorType = 'CUSTOMER' and l.actorId = :customerId) "
            + "or l.applicationId in (select a.id from LoanApplication a where a.customerId = :customerId) order by l.id desc")
    List<ActivityLog> findForCustomer(@Param("customerId") long customerId, Pageable page);
}
