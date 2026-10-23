package vn.kltn.tiensanglocvay.policy;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParamVersionRepository extends JpaRepository<ParamVersion, Integer> {

    /** Tối đa một bản ghi nhờ unique index uq_param_version_active. */
    Optional<ParamVersion> findByStatus(String status);
}
