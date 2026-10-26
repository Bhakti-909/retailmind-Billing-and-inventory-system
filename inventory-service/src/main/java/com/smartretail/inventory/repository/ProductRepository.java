package com.smartretail.inventory.repository;

import com.smartretail.inventory.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

/**
 * All lookup methods include businessId. This is the data isolation boundary:
 * a token issued for one business can never read or mutate another business's
 * products, even when the product id is guessed.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByBusinessIdOrderByNameAsc(Long businessId);

    Optional<Product> findByIdAndBusinessId(Long id, Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.businessId = :businessId")
    Optional<Product> findByIdAndBusinessIdForUpdate(@Param("id") Long id, @Param("businessId") Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids and p.businessId = :businessId order by p.id")
    List<Product> findAllForUpdateByIdsAndBusinessId(Collection<Long> ids, Long businessId);

    boolean existsByBusinessIdAndSkuIgnoreCase(Long businessId, String sku);

    boolean existsByBusinessIdAndSkuIgnoreCaseAndIdNot(Long businessId, String sku, Long id);

    boolean existsByBusinessIdAndBarcodeIgnoreCase(Long businessId, String barcode);

    boolean existsByBusinessIdAndBarcodeIgnoreCaseAndIdNot(Long businessId, String barcode, Long id);

    @Query("select p from Product p where p.businessId = :businessId " +
           "and p.quantity <= p.reorderLevel order by p.quantity asc, p.name asc")
    List<Product> findLowStockByBusinessId(@Param("businessId") Long businessId);
}
