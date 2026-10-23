package vn.kltn.tiensanglocvay.policy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyParamRepository extends JpaRepository<PolicyParam, Long> {

    List<PolicyParam> findByVersionIdOrderById(Integer versionId);

    @Modifying
    @Query("delete from PolicyParam p where p.versionId = :versionId")
    void deleteByVersion(@Param("versionId") Integer versionId);
}
