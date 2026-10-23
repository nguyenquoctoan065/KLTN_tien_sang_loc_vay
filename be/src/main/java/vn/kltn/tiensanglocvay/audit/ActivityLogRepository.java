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

    /**
     * GET /admin/logs: tối đa 500 dòng mới nhất, mỗi dòng {@code [id, mã hồ sơ, tên người thực hiện]}. Tên như mock: khách là họ tên hoặc
     * "Khách hàng " + 4 số cuối điện thoại (ApplicationSupport.customerName), nhân viên là họ tên, còn lại "Hệ thống".
     * {@code type} rỗng bỏ lọc; {@code like} là từ khóa chữ thường đã thoát {@code % _ \} và bọc {@code %…%}, khớp chuỗi
     * "tên thao tác chi tiết mã hồ sơ" (mock ghép các trường bằng dấu cách rồi tìm).
     */
    @Query(nativeQuery = true, value = """
            select x.id, x.code, x.actor_name from (
              select l.id, l.created_at, l.actor_type, l.action, coalesce(l.detail, '') as detail, a.code,
                     case l.actor_type
                       when 'CUSTOMER' then coalesce(c.full_name, 'Khách hàng ' || right(c.phone, 4), 'Khách hàng')
                       when 'STAFF' then coalesce(s.full_name, 'Nhân viên')
                       else 'Hệ thống' end as actor_name
              from activity_log l
              left join customer c on l.actor_type = 'CUSTOMER' and c.id = l.actor_id
              left join staff s on l.actor_type = 'STAFF' and s.id = l.actor_id
              left join loan_application a on a.id = l.application_id
            ) x
            where (:type = '' or x.actor_type = :type)
              and (:like = '' or lower(x.actor_name || ' ' || x.action || ' ' || x.detail || ' ' || coalesce(x.code, '')) like :like escape '\\')
            order by x.created_at desc, x.id desc
            limit 500
            """)
    List<Object[]> searchAdmin(@Param("type") String type, @Param("like") String like);
}
