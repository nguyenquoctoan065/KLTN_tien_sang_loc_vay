package vn.kltn.tiensanglocvay.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductRepository extends JpaRepository<LoanProduct, Integer> {

    List<LoanProduct> findByStatusOrderById(String status);
}
