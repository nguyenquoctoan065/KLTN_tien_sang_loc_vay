package vn.kltn.tiensanglocvay.screening;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    Optional<Screening> findByPublicId(UUID publicId);

    List<Screening> findByCustomerIdOrderByIdDesc(Long customerId);

    long countByCustomerId(Long customerId);

    /**
     * Lượt sàng lọc đã lưu của khách hàng và không có phương án nào đã lập hồ sơ (kể cả hồ sơ đã hủy: hồ sơ còn tham chiếu phương án
     * nên không xóa được, mock cũng giữ). Dùng khi rút đồng ý phần (a).
     */
    @Query("select s.id from Screening s where s.customerId = :customerId and not exists "
            + "(select 1 from LoanPlan p, LoanApplication a where p.screeningId = s.id and a.planId = p.id) order by s.id")
    List<Long> findUnlinkedIds(@Param("customerId") long customerId);
}
