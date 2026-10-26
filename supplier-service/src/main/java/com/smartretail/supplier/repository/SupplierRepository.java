package com.smartretail.supplier.repository;
import com.smartretail.supplier.entity.Supplier; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SupplierRepository extends JpaRepository<Supplier,Long>{ List<Supplier> findAllByBusinessIdOrderByNameAsc(Long businessId); Optional<Supplier> findByIdAndBusinessId(Long id,Long businessId); boolean existsByBusinessIdAndPhone(Long businessId,String phone); boolean existsByBusinessIdAndPhoneAndIdNot(Long businessId,String phone,Long id); }
