package vn.kltn.tiensanglocvay.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Integer> {

    List<Branch> findByStatusOrderById(String status);
}
