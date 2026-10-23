package vn.kltn.tiensanglocvay.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPhone(String phone);

    boolean existsByPhone(String phone);

    @Query("select c from Customer c where lower(c.email) = lower(:email)")
    Optional<Customer> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(c) > 0 from Customer c where lower(c.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}
