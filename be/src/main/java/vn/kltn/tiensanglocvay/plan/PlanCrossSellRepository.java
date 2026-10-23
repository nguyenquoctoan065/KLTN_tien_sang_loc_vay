package vn.kltn.tiensanglocvay.plan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCrossSellRepository extends JpaRepository<PlanCrossSell, PlanCrossSell.Key> {

    List<PlanCrossSell> findByPlanIdOrderByCrossSellId(Long planId);
}
