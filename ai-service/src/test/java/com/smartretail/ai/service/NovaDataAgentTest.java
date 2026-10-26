package com.smartretail.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class NovaDataAgentTest {
	@Test
	void comparesMonthToDateWithMatchingDaysOfPreviousMonthUsingInclusiveLocalDates() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
			"invoice_count", 5L, "total_sales", 1000, "gross_profit", 300));
		NovaDataAgent data = new NovaDataAgent(jdbc, new NovaForecastAgent(new ForecastEngine()));
		NovaIntentClassifier classifier = new NovaIntentClassifier();
		var classification = classifier.classify("compare this month with last month sales", List.of());
		var tools = new NovaToolRouter().route(classification);

		Map<String, Object> result = data.collectLiveData(21L, "compare this month with last month sales", classification, tools);
		@SuppressWarnings("unchecked")
		Map<String, Object> comparison = (Map<String, Object>) result.get("periodComparison");
		@SuppressWarnings("unchecked")
		Map<String, Object> current = (Map<String, Object>) comparison.get("currentPeriod");
		@SuppressWarnings("unchecked")
		Map<String, Object> previous = (Map<String, Object>) comparison.get("previousPeriod");
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		LocalDate previousMonth = today.minusMonths(1).withDayOfMonth(1);
		assertEquals(today.withDayOfMonth(1).toString(), current.get("from"));
		assertEquals(today.toString(), current.get("to"));
		assertEquals(previousMonth.toString(), previous.get("from"));
		assertEquals(previousMonth.withDayOfMonth(Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth())).toString(), previous.get("to"));

		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc, times(3)).queryForMap(anyString(), arguments.capture());
		Object[] currentSummaryQuery = arguments.getAllValues().get(1);
		Object[] previousSummaryQuery = arguments.getAllValues().get(2);
		assertEquals(LocalDateTime.of(today.withDayOfMonth(1), java.time.LocalTime.MIDNIGHT), currentSummaryQuery[1]);
		assertEquals(LocalDateTime.of(today.plusDays(1), java.time.LocalTime.MIDNIGHT), currentSummaryQuery[2]);
		assertEquals(LocalDateTime.of(previousMonth, java.time.LocalTime.MIDNIGHT), previousSummaryQuery[1]);
		assertEquals(LocalDateTime.of(previousMonth.withDayOfMonth(Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth())).plusDays(1), java.time.LocalTime.MIDNIGHT), previousSummaryQuery[2]);
		assertTrue(String.valueOf(comparison.get("basis")).contains("inclusive start and end dates"));
	}

	@Test
	void productPerformanceUsesInvoiceDiscountAllocationAndGstTaxableAmounts() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
		NovaDataAgent data = new NovaDataAgent(jdbc, new NovaForecastAgent(new ForecastEngine()));
		NovaIntentClassifier classifier = new NovaIntentClassifier();
		var classification = classifier.classify("show best selling products", List.of());
		data.collectLiveData(31L, "show best selling products", classification, new NovaToolRouter().route(classification));
		verify(jdbc).queryForList(argThat(sql -> sql.contains("proportional_discount")
			&& sql.contains("gst_line_taxable") && sql.contains("line_discount")), any(Object[].class));
	}

	@Test
	void retentionAnalysisUsesAggregateCustomerWindowsWithoutLoadingCustomerNames() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class))).thenReturn(4L);
		when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
			"invoice_count", 10L, "total_sales", 2000, "gross_profit", 500),
			Map.of("customers_with_invoices_90d", 8L, "repeat_customers_90d", 5L,
				"new_customers_30d", 2L, "active_customers_30d", 6L, "lapsed_customers_90d", 2L));
		NovaDataAgent data = new NovaDataAgent(jdbc, new NovaForecastAgent(new ForecastEngine()));
		NovaIntentClassifier classifier = new NovaIntentClassifier();
		var classification = classifier.classify("how can I improve customer retention?", List.of());

		Map<String, Object> result = data.collectLiveData(41L, "how can I improve customer retention?",
			classification, new NovaToolRouter().route(classification));

		@SuppressWarnings("unchecked")
		Map<String, Object> customers = (Map<String, Object>) result.get("customerAnalysis");
		assertEquals(8L, ((Map<?, ?>) customers.get("retentionSummary")).get("customers_with_invoices_90d"));
		verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
		verify(jdbc, times(1)).queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(Object[].class));
	}

	@Test
	void lossRecoveryBaselineUsesTenantScopedTwentyEightDayWindow() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
			"invoice_count", 12L, "net_revenue_28d", 8400, "gross_profit_28d", 2100));
		NovaDataAgent data = new NovaDataAgent(jdbc, new NovaForecastAgent(new ForecastEngine()));
		NovaIntentClassifier classifier = new NovaIntentClassifier();
		String question = "how can I cover today's loss within 7 days";
		var classification = classifier.classify(question, List.of());
		Map<String, Object> result = data.collectLiveData(51L, question, classification,
			new NovaToolRouter().route(classification));

		@SuppressWarnings("unchecked")
		Map<String, Object> baseline = (Map<String, Object>) result.get("lossRecoveryBaseline");
		assertEquals(12L, baseline.get("invoice_count"));
		assertEquals(8400, baseline.get("net_revenue_28d"));
		assertTrue(String.valueOf(baseline.get("period")).contains(LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(27).toString()));
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).queryForMap(argThat(sql -> sql.contains("SUM(revenue - cost)") && sql.contains("business_id = ?")), arguments.capture());
		Object[] args = arguments.getValue();
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		assertEquals(51L, args[0]);
		assertEquals(LocalDateTime.of(today.minusDays(27), java.time.LocalTime.MIDNIGHT), args[1]);
		assertEquals(LocalDateTime.of(today.plusDays(1), java.time.LocalTime.MIDNIGHT), args[2]);
	}
}
