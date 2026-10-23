package vn.kltn.tiensanglocvay.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrossSellProductRepository extends JpaRepository<CrossSellProduct, Integer> {

    List<CrossSellProduct> findByStatusOrderById(String status);
}
