package com.smartretail.customer.repository;

import com.smartretail.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByBusinessIdOrderByNameAsc(Long businessId);
    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);
    boolean existsByBusinessIdAndPhoneAndIdNot(Long businessId, String phone, Long id);
    boolean existsByBusinessIdAndPhone(Long businessId, String phone);
}
