package vn.kltn.tiensanglocvay.application;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRequestFormRepository extends JpaRepository<LoanRequestForm, Long> {

    Optional<LoanRequestForm> findByApplicationId(Long applicationId);
}
