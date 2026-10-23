package vn.kltn.tiensanglocvay.screening;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    /** Các lần đồng ý của khách hàng, mới nhất trước (GET /me/data). */
    List<Consent> findByCustomerIdOrderByIdDesc(Long customerId);

    /** Các lần đồng ý của một phạm vi (A hoặc B) chưa rút (POST /me/consents/withdraw). */
    List<Consent> findByCustomerIdAndScopeAndWithdrawnAtIsNull(Long customerId, String scope);
}
