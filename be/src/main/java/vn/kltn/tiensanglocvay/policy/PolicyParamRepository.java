package vn.kltn.tiensanglocvay.policy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyParamRepository extends JpaRepository<PolicyParam, Long> {

    List<PolicyParam> findByVersionId(Integer versionId);
}
