package vn.kltn.tiensanglocvay.application;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    Optional<LoanApplication> findByPublicId(UUID publicId);

    /** Khóa dòng hồ sơ đến hết giao dịch: hai nhân viên không ghi kết quả cùng lúc cho một hồ sơ. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoanApplication a where a.publicId = :publicId")
    Optional<LoanApplication> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    Optional<LoanApplication> findByQrToken(String qrToken);

    /** Hồ sơ của khách hàng theo thứ tự tạo (GET /me/data, như thứ tự của mock). */
    List<LoanApplication> findByCustomerIdOrderByIdAsc(Long customerId);

    /** Khóa các hồ sơ của khách hàng đến hết giao dịch (rút đồng ý không chạy song song với nhân viên đang đối chiếu). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoanApplication a where a.customerId = :customerId order by a.id")
    List<LoanApplication> findByCustomerIdForUpdate(@Param("customerId") long customerId);

    List<LoanApplication> findByCustomerIdOrderByIdDesc(Long customerId);

    boolean existsByPlanIdAndStatusNot(Long planId, ApplicationStatus status);

    long countByCodeStartingWith(String prefix);

    /**
     * 5.1 Tra cứu hồ sơ của chi nhánh (mock GET /staff/applications): trừ {@code excluded} (DRAFTING); {@code anyStatus} bỏ lọc trạng thái;
     * {@code like} là từ khóa chữ thường đã thoát {@code % _ \} và bọc {@code %…%}, khớp mã hồ sơ, họ tên, số điện thoại;
     * {@code qr} khớp đúng mã QR (chữ hoa). {@code keyword} rỗng thì bỏ lọc từ khóa.
     */
    @Query("""
            select a from LoanApplication a, Customer c
            where c.id = a.customerId and a.branchId = :branchId and a.status <> :excluded
              and (:anyStatus = true or a.status in :statuses)
              and (:keyword = ''
                   or lower(a.code) like :like escape '\\'
                   or lower(coalesce(a.fullName, '')) like :like escape '\\'
                   or c.phone like :like escape '\\'
                   or a.qrToken = :qr)
            order by a.updatedAt desc, a.id desc
            """)
    List<LoanApplication> searchBranch(@Param("branchId") Integer branchId, @Param("excluded") ApplicationStatus excluded,
                                       @Param("anyStatus") boolean anyStatus, @Param("statuses") Collection<ApplicationStatus> statuses,
                                       @Param("keyword") String keyword, @Param("like") String like, @Param("qr") String qr);

    /** Số hồ sơ theo trạng thái của một chi nhánh: mỗi dòng {@code [ApplicationStatus, Long]}. */
    @Query("select a.status, count(a) from LoanApplication a where a.branchId = :branchId group by a.status")
    List<Object[]> countByStatus(@Param("branchId") Integer branchId);
}
