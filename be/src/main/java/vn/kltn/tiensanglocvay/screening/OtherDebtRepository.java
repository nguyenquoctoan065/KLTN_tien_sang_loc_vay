package vn.kltn.tiensanglocvay.screening;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtherDebtRepository extends JpaRepository<OtherDebt, Long> {

    List<OtherDebt> findByScreeningIdOrderById(Long screeningId);
}
