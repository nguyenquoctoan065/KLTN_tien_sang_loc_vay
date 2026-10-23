package vn.kltn.tiensanglocvay.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    Optional<LoanApplication> findByPublicId(UUID publicId);

    List<LoanApplication> findByCustomerIdOrderByIdDesc(Long customerId);

    boolean existsByPlanIdAndStatusNot(Long planId, ApplicationStatus status);

    long countByCodeStartingWith(String prefix);
}
