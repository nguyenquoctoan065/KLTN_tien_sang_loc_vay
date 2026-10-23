package vn.kltn.tiensanglocvay.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByTargetAndPurposeAndUsedAtIsNullOrderByIdDesc(String target, String purpose);

    /** Đánh dấu đã dùng; trả 0 nếu mã đã bị dùng trước đó (hai yêu cầu đồng thời). */
    @Modifying
    @Query("update OtpCode o set o.usedAt = :now where o.id = :id and o.usedAt is null")
    int markUsed(@Param("id") long id, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update OtpCode o set o.usedAt = :now where o.target = :target and o.purpose = :purpose and o.usedAt is null")
    int invalidate(@Param("target") String target, @Param("purpose") String purpose, @Param("now") OffsetDateTime now);
}
