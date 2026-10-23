package vn.kltn.tiensanglocvay.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.kltn.tiensanglocvay.security.AccountKind;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    @Query("select s from AuthSession s where s.tokenHash = :hash and s.revokedAt is null and s.expiresAt > :now")
    Optional<AuthSession> findActive(@Param("hash") String hash, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now where s.tokenHash = :hash and s.revokedAt is null")
    int revoke(@Param("hash") String hash, @Param("now") OffsetDateTime now);

    /** Thu hồi mọi phiên còn hiệu lực của tài khoản, trừ phiên có {@code keepHash} (truyền chuỗi rỗng để thu hồi hết). */
    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now where s.accountKind = :kind and s.accountId = :id "
            + "and s.revokedAt is null and s.tokenHash <> :keepHash")
    int revokeAll(@Param("kind") AccountKind kind, @Param("id") long id, @Param("keepHash") String keepHash,
                  @Param("now") OffsetDateTime now);
}
