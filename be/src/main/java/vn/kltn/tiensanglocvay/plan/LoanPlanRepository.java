package vn.kltn.tiensanglocvay.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanPlanRepository extends JpaRepository<LoanPlan, Long> {

    Optional<LoanPlan> findByPublicId(UUID publicId);

    /** Phương án của khách hàng (qua lượt sàng lọc), mới nhất trước. */
    @Query("select p from LoanPlan p, Screening s where s.id = p.screeningId and s.customerId = :customerId order by p.id desc")
    List<LoanPlan> findByCustomer(@Param("customerId") long customerId);

    @Query("select count(p) from LoanPlan p, Screening s where s.id = p.screeningId and s.customerId = :customerId")
    long countByCustomer(@Param("customerId") long customerId);

    @Query("select s.customerId from LoanPlan p, Screening s where p.id = :planId and s.id = p.screeningId")
    Optional<Long> findCustomerId(@Param("planId") long planId);

    @Query("select s.publicId from Screening s where s.id = :screeningId")
    Optional<UUID> findScreeningPublicId(@Param("screeningId") long screeningId);

    /** Mã công khai của hồ sơ chưa hủy lập từ phương án (bảng loan_application, entity làm ở B4). */
    @Query(value = "select public_id from loan_application where plan_id = :planId and status <> 'CANCELLED' order by id desc limit 1",
            nativeQuery = true)
    Optional<UUID> findOpenApplicationPublicId(@Param("planId") long planId);
}
