package com.smartretail.billing.repository;
import com.smartretail.billing.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.time.Instant;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Invoice> findTop100ByPaymentStatusAndPaymentMethodAndPaymentDueAtLessThanEqualAndPaymentReminderSentAtIsNullOrderByPaymentDueAtAsc(
            String paymentStatus, String paymentMethod, Instant dueAt);
    List<Invoice> findAllByBusinessIdAndSoldAtBetweenOrderBySoldAtDesc(
	    Long businessId, LocalDateTime start, LocalDateTime end);

    List<Invoice> findAllByBusinessIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtDesc(
	    Long businessId, LocalDateTime start, LocalDateTime end);

	List<Invoice> findTop6ByBusinessIdOrderBySoldAtDesc(Long businessId);

	List<Invoice> findAllByBusinessIdOrderBySoldAtDesc(Long businessId);

	List<Invoice> findAllByBusinessIdAndBuyerNameContainingIgnoreCaseOrderBySoldAtDesc(Long businessId, String buyerName);

	List<Invoice> findAllByBusinessIdAndCustomerIdOrderBySoldAtDesc(Long businessId, Long customerId);

	List<Invoice> findAllByBusinessIdAndCustomerIdIsNullAndBuyerNameIgnoreCaseOrderBySoldAtDesc(Long businessId, String buyerName);
}

