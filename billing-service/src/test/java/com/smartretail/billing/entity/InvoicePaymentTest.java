package com.smartretail.billing.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoicePaymentTest {
	@Test
	void balanceModeOverridesStaleSavedPaidValues() {
		Invoice invoice = new Invoice();
		invoice.setPaymentMethod("Balance");
		invoice.setTotalAmount(new BigDecimal("195.00"));
		invoice.setPaidAmount(new BigDecimal("195.00"));
		invoice.setBalanceDue(BigDecimal.ZERO);
		invoice.setPaymentStatus("PAID");

		assertEquals(BigDecimal.ZERO, invoice.getPaidAmount());
		assertEquals(new BigDecimal("195.00"), invoice.getBalanceDue());
		assertEquals("UNPAID", invoice.getPaymentStatus());
	}

	@Test
	void cashModeKeepsPersistedPaymentAmounts() {
		Invoice invoice = new Invoice();
		invoice.setPaymentMethod("Cash");
		invoice.setTotalAmount(new BigDecimal("195.00"));
		invoice.setPaidAmount(new BigDecimal("195.00"));
		invoice.setBalanceDue(BigDecimal.ZERO);
		invoice.setPaymentStatus("PAID");

		assertEquals(new BigDecimal("195.00"), invoice.getPaidAmount());
		assertEquals(BigDecimal.ZERO, invoice.getBalanceDue());
		assertEquals("PAID", invoice.getPaymentStatus());
	}
}
