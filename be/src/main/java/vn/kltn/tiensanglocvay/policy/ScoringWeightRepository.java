package vn.kltn.tiensanglocvay.policy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoringWeightRepository extends JpaRepository<ScoringWeight, Long> {

    List<ScoringWeight> findByVersionIdOrderById(Integer versionId);

    @Modifying
    @Query("delete from ScoringWeight w where w.versionId = :versionId")
    void deleteByVersion(@Param("versionId") Integer versionId);
}
