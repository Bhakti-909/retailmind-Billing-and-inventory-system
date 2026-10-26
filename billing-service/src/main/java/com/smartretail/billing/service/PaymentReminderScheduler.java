package com.smartretail.billing.service;

import com.smartretail.billing.entity.BusinessProfile;
import com.smartretail.billing.entity.Invoice;
import com.smartretail.billing.repository.BusinessProfileRepository;
import com.smartretail.billing.repository.InvoiceRepository;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class PaymentReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(PaymentReminderScheduler.class);
    private final InvoiceRepository invoices;
    private final BusinessProfileRepository businesses;
    private final JdbcTemplate jdbcTemplate;
    private final InvoiceEmailService emailService;

    public PaymentReminderScheduler(InvoiceRepository invoices, BusinessProfileRepository businesses,
                                    JdbcTemplate jdbcTemplate, InvoiceEmailService emailService) {
        this.invoices = invoices;
        this.businesses = businesses;
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
    }

    @Scheduled(fixedDelayString = "${billing.payment-reminder.poll-ms:60000}")
    @Transactional
    public void sendDuePaymentReminders() {
        List<Invoice> dueInvoices = invoices
                .findTop100ByPaymentStatusAndPaymentMethodAndPaymentDueAtLessThanEqualAndPaymentReminderSentAtIsNullOrderByPaymentDueAtAsc(
                        "UNPAID", "Balance", Instant.now());
        for (Invoice invoice : dueInvoices) {
            try {
                if (invoice.getCustomerId() == null) continue;
                // Recheck persisted payment values before delivery so a bill already settled
                // after being selected by the scheduler does not receive a stale reminder.
                if (!"UNPAID".equalsIgnoreCase(invoice.getPaymentStatus())
                        || invoice.getBalanceDue().signum() <= 0) continue;
                List<CustomerBillingDetails> customers = jdbcTemplate.query(
                        "SELECT name, phone, email, address FROM customers WHERE id = ? AND business_id = ?",
                        (rs, row) -> new CustomerBillingDetails(rs.getString("name"), rs.getString("phone"),
                                rs.getString("email"), rs.getString("address")),
                        invoice.getCustomerId(), invoice.getBusinessId());
                if (customers.isEmpty() || customers.get(0).email() == null
                        || customers.get(0).email().isBlank()) continue;
                BusinessProfile business = businesses.findById(invoice.getBusinessId()).orElse(null);
                if (business == null) continue;
                CustomerBillingDetails customer = customers.get(0);
                emailService.sendPaymentReminder(customer.email().trim(), business, invoice,
                        customer.phone(), customer.address());
                invoice.setPaymentReminderSentAt(Instant.now());
                invoices.save(invoice);
                log.info("Payment reminder sent for invoice {}", invoice.getInvoiceNumber());
            } catch (MessagingException | RuntimeException error) {
                log.warn("Payment reminder delivery failed for invoice {} ({}). It will be retried.",
                        invoice.getInvoiceNumber(), error.getClass().getSimpleName());
            }
        }
    }

    private record CustomerBillingDetails(String name, String phone, String email, String address) {}
}
