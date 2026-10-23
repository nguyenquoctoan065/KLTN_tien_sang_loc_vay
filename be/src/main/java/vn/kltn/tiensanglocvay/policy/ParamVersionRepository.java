package vn.kltn.tiensanglocvay.policy;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParamVersionRepository extends JpaRepository<ParamVersion, Integer> {

    /** Tối đa một bản ghi cho ACTIVE / DRAFT nhờ unique index uq_param_version_active, uq_param_version_draft. */
    Optional<ParamVersion> findByStatus(String status);

    boolean existsByStatus(String status);

    boolean existsByVersionNo(String versionNo);

    /** Lịch sử phiên bản, mới nhất trước (mock: thứ tự tạo đảo ngược). */
    List<ParamVersion> findAllByOrderByIdDesc();

    /** Khóa dòng phiên bản: sửa, chạy thử, xóa, kích hoạt cùng một bản nháp không chạy chồng nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ParamVersion v where v.id = :id")
    Optional<ParamVersion> findByIdForUpdate(@Param("id") Integer id);

    /** Khóa bản đang áp dụng khi kích hoạt bản mới. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ParamVersion v where v.status = 'ACTIVE'")
    Optional<ParamVersion> findActiveForUpdate();
}
