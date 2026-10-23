package vn.kltn.tiensanglocvay.application;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationDetailRepository extends JpaRepository<VerificationDetail, VerificationDetail.Key> {

    List<VerificationDetail> findByVerificationIdIn(Collection<Long> verificationIds);
}
