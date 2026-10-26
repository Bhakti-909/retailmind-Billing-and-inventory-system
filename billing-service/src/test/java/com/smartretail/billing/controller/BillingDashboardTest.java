package com.smartretail.billing.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.smartretail.billing.repository.BusinessProfileRepository;
import com.smartretail.billing.repository.InvoiceRepository;
import com.smartretail.billing.service.InventoryStockClient;
import com.smartretail.billing.service.InvoiceEmailService;

class BillingDashboardTest {
	private BillingController controller(InvoiceRepository invoices) {
		return new BillingController(invoices, mock(BusinessProfileRepository.class),
				mock(InventoryStockClient.class), mock(InvoiceEmailService.class), mock(JdbcTemplate.class));
	}

	@Test
	void dashboardReturnsEmptyPeriodBucketsAndPaymentTotalsForDashboardCards() {
		InvoiceRepository invoices = mock(InvoiceRepository.class);
		when(invoices.findAllByBusinessIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtDesc(
				eq(7L), any(), any())).thenReturn(List.of());
		when(invoices.findTop6ByBusinessIdOrderBySoldAtDesc(7L)).thenReturn(List.of());
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getAttribute("businessId")).thenReturn(7L);

		Map<String, Object> response = controller(invoices).dashboard(7, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07"), request);

		assertEquals(7L, response.get("days"));
		assertEquals("2026-10-01", response.get("from"));
		assertEquals("2026-10-07", response.get("to"));
		assertEquals(7, ((List<?>) response.get("dailySales")).size());
		assertEquals(List.of(), response.get("topProducts"));
		assertEquals(List.of(), response.get("recentSales"));
		Map<?, ?> payments = (Map<?, ?>) response.get("paymentSummary");
		assertEquals(0, ((Number) payments.get("collectedAmount")).intValue());
		assertEquals(0, ((Number) payments.get("outstandingAmount")).intValue());
	}

	@Test
	void dashboardRejectsRangesLongerThan366DaysBeforeQuerying() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		ResponseStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
				() -> controller(mock(InvoiceRepository.class)).dashboard(367, LocalDate.parse("2025-10-01"), LocalDate.parse("2026-10-02"), request));
		assertEquals(400, error.getStatusCode().value());
		assertNotNull(error.getReason());
	}
}
