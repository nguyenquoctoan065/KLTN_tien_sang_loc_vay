package vn.kltn.tiensanglocvay.screening;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    Optional<Screening> findByPublicId(UUID publicId);

    List<Screening> findByCustomerIdOrderByIdDesc(Long customerId);
}
