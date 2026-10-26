package com.smartretail.billing.service;

import com.smartretail.billing.entity.BusinessProfile;
import com.smartretail.billing.entity.Invoice;
import com.smartretail.billing.entity.SaleItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GmailInvoiceEmailSmokeTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_REAL_EMAIL_SMOKE_TEST", matches = "true")
    void sendsASyntheticInvoiceToTheConfiguredSenderAccount() throws Exception {
        String sender = requiredEnv("MAIL_USERNAME");
        String password = requiredEnv("MAIL_PASSWORD");
        String host = System.getenv().getOrDefault("MAIL_HOST", "smtp.gmail.com");
        int port = Integer.parseInt(System.getenv().getOrDefault("MAIL_PORT", "587"));

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(sender);
        mailSender.setPassword(password);
        mailSender.setDefaultEncoding("UTF-8");
        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "8000");
        properties.put("mail.smtp.writetimeout", "8000");

        BusinessProfile business = mock(BusinessProfile.class);
        when(business.getShopName()).thenReturn("Smart Retail Email Test");
        when(business.getShopAddress()).thenReturn("Test message only");
        when(business.getCity()).thenReturn("Pune");
        when(business.getState()).thenReturn("Maharashtra");
        when(business.getPostalCode()).thenReturn("411001");
        when(business.getOwnerContact()).thenReturn(null);
        when(business.getOwnerEmail()).thenReturn(sender);
        when(business.getGstin()).thenReturn("27AAAAA0000A1Z5");

        SaleItem item = new SaleItem();
        item.setProductName("Sample test item");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("112.00"));
        item.setUnitCost(new BigDecimal("80.00"));
        item.setMrpPrice(new BigDecimal("112.00"));
        item.setGstRate(new BigDecimal("12.00"));
        item.setTaxableAmount(new BigDecimal("100.00"));
        item.setGstAmount(new BigDecimal("12.00"));
        item.setBillDiscount(BigDecimal.ZERO);

        Invoice invoice = new Invoice();
        invoice.setBusinessId(1L);
        invoice.setSellerGstin("27AAAAA0000A1Z5");
        invoice.setGstInvoice(true);
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTaxableAmount(new BigDecimal("100.00"));
        invoice.setGstRate(new BigDecimal("12.00"));
        invoice.setCgstAmount(new BigDecimal("6.00"));
        invoice.setSgstAmount(new BigDecimal("6.00"));
        invoice.setIgstAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(new BigDecimal("112.00"));
        invoice.setBuyerName("SMTP connectivity test");
        invoice.setInvoiceNumber("TEST-" + System.currentTimeMillis());
        invoice.setSoldAt(LocalDateTime.now());
        invoice.setRevenue(new BigDecimal("100.00"));
        invoice.setCost(new BigDecimal("80.00"));
        invoice.setItems(List.of(item));

        InvoiceEmailService invoiceEmailService = new InvoiceEmailService(mailSender, sender);
        invoiceEmailService.sendInvoice(sender, business, invoice, null, null);
        System.out.println("Synthetic invoice email accepted by SMTP and addressed to MAIL_USERNAME.");
    }

    private String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " must be set to run the real Gmail smoke test.");
        }
        return value;
    }
}
