package com.smartretail.reports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DashboardCustomerInsightsTest {
	@Test
	void customerReportReturnsPeriodSummaryAndTopCustomersForDashboard() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		Map<String, Object> summary = Map.of(
				"customer_count", 3L,
				"bill_count", 5L,
				"customer_sales", new BigDecimal("2250.00"),
				"average_bill", new BigDecimal("450.00"));
		Map<String, Object> firstCustomer = Map.of(
				"customer_name", "Asha Patel",
				"bill_count", 3L,
				"total_spend", new BigDecimal("1500.00"));
		List<Map<String, Object>> topCustomers = List.of(firstCustomer);
		when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(summary);
		when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(topCustomers);

		ReportService reports = new ReportService(jdbc);
		Map<String, Object> result = reports.customers(42L, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-30"));

		assertEquals("2026-10-01", result.get("from"));
		assertEquals("2026-10-30", result.get("to"));
		assertSame(summary, result.get("summary"));
		assertSame(topCustomers, result.get("topCustomers"));
		verify(jdbc).queryForMap(anyString(), any(Object[].class));
		verify(jdbc).queryForList(anyString(), any(Object[].class));
	}
}
