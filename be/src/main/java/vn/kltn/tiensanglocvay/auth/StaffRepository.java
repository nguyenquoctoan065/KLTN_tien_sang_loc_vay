package vn.kltn.tiensanglocvay.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRepository extends JpaRepository<Staff, Integer> {

    Optional<Staff> findByEmail(String email);

    /** Tên chi nhánh cho AuthUser.branchName (entity Branch làm ở giai đoạn B2). */
    @Query(value = "select name from branch where id = :id", nativeQuery = true)
    Optional<String> findBranchName(@Param("id") Integer id);
}
