package vn.kltn.tiensanglocvay.catalog;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, Integer> {

    List<Branch> findByStatusOrderById(String status);

    List<Branch> findAllByOrderById();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Integer id);

    /** Khóa dòng chi nhánh: hai lần ngừng / mở lại cùng một chi nhánh không chạy chồng nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id = :id")
    Optional<Branch> findByIdForUpdate(@Param("id") Integer id);
}
