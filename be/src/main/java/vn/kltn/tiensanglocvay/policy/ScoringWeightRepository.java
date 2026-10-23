package vn.kltn.tiensanglocvay.policy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringWeightRepository extends JpaRepository<ScoringWeight, Long> {

    List<ScoringWeight> findByVersionIdOrderById(Integer versionId);
}
