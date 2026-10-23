package vn.kltn.tiensanglocvay.application;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalResultRepository extends JpaRepository<ApprovalResult, Long> {

    Optional<ApprovalResult> findFirstByApplicationIdOrderByIdDesc(Long applicationId);
}
