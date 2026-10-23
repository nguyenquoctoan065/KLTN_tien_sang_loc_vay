package vn.kltn.tiensanglocvay.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, Integer> {

    Optional<Staff> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Staff> findAllByOrderById();

    List<Staff> findByBranchIdAndRole(Integer branchId, String role);
}
