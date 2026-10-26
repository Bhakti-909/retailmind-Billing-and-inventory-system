package com.smartretail.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiAgentServiceTest {
	private JdbcTemplate jdbc;
	private AiAgentService service;

	@BeforeEach
	void setUp() {
		jdbc = mock(JdbcTemplate.class);
		RestClient.Builder builder = mock(RestClient.Builder.class);
		when(builder.requestFactory(any(ClientHttpRequestFactory.class))).thenReturn(builder);
		when(builder.baseUrl(anyString())).thenReturn(builder);
		when(builder.build()).thenReturn(mock(RestClient.class));
		when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
			"total_bills", 32L,
			"total_sales", new BigDecimal("12500.00"),
			"items_sold", 57L));
		service = new AiAgentService(jdbc, new KnowledgeRetriever(), new ForecastEngine(), new NovaIntentClassifier(), new NovaToolRouter(), new NovaCalculationEngine(), builder,
			"http://localhost:11434", "test-model", 2);
	}

	@Test
	void returnsDatabaseBackedTodaySalesForEnglishAndCurrencyRequests() {
		String answer = (String) service.ask(17L, "show todays salse in INR please").get("answer");
		assertTrue(answer.contains("Total Sales: ₹12,500"));
		assertTrue(answer.contains("Total Bills: 32"));
		assertTrue(answer.contains("Items Sold: 57"));
		assertTrue(answer.contains("Returns: Not tracked"));
		verify(jdbc).queryForMap(anyString(), any(Object[].class));

		String dollarsAnswer = (String) service.ask(17L, "today sales in dollars").get("answer");
		assertTrue(dollarsAnswer.contains("₹12,500"));
		assertTrue(dollarsAnswer.contains("no exchange rate is available"));
	}

	@Test
	void scopesTodaySalesToInclusiveBusinessDateAndExclusiveNextDayBoundary() {
		service.ask(17L, "today's sales");
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).queryForMap(anyString(), arguments.capture());
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		Object[] queryArguments = arguments.getValue();
		assertEquals(6, queryArguments.length);
		assertEquals(LocalDateTime.of(today, LocalTime.MIDNIGHT), queryArguments[1]);
		assertEquals(LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT), queryArguments[2]);
		assertEquals(LocalDateTime.of(today, LocalTime.MIDNIGHT), queryArguments[4]);
		assertEquals(LocalDateTime.of(today.plusDays(1), LocalTime.MIDNIGHT), queryArguments[5]);
	}

	@Test
	void understandsHindiMarathiAndFollowUpSalesQuestions() {
		String hindi = (String) service.ask(17L, "aaj ki bikri kitni hai?").get("answer");
		assertTrue(hindi.contains("कुल बिक्री"));
		String marathi = (String) service.ask(17L, "आजची विक्री किती आहे?").get("answer");
		assertTrue(marathi.contains("एकूण विक्री"));
		String followUp = (String) service.ask(17L, "and today?", List.of(
			new AiAgentService.ChatTurn("user", "show my sales"),
			new AiAgentService.ChatTurn("assistant", "I can summarize the selected sales period."))).get("answer");
		assertTrue(followUp.contains("Total Sales: ₹12,500"));
	}

	@Test
	void answersInformalLastSevenDaysSalesFromBusinessTotals() {
		when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
			"invoice_count", 12L,
			"total_sales", new BigDecimal("18750.50"),
			"gross_profit", new BigDecimal("4200.00"),
			"average_invoice", new BigDecimal("1562.54"),
			"paying_customers", 9L,
			"discounts_given", new BigDecimal("250.00"),
			"tax_collected", new BigDecimal("2800.00"),
			"items_sold", 31L,
			"last_sale_at", "2026-10-11T12:00:00"));

		String answer = (String) service.ask(17L, "my query is last 7 day sell").get("answer");

		assertTrue(answer.contains("Recorded sales from "));
		assertTrue(answer.contains("Total sales: INR 18,750.5"));
		assertTrue(answer.contains("Bills: 12"));
		assertTrue(answer.contains("Items sold: 31"));
		verify(jdbc).queryForMap(anyString(), any(Object[].class));
	}

	@Test
	void calculatesPeriodGrossProfitAndReportsNetProfitAsUnavailable() {
		when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(
			Map.of("net_revenue", new BigDecimal("1000.00"), "cost_of_goods", new BigDecimal("750.00"),
				"gross_profit", new BigDecimal("250.00"), "invoice_count", 4L),
			Map.of("invoice_count", 4L, "total_sales", new BigDecimal("1000.00")));
		when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
			Map.of("product_name", "Tea", "gross_margin_percent", new BigDecimal("8.50"))));

		String answer = (String) service.ask(17L, "profit and loss for last 7 days").get("answer");

		assertTrue(answer.contains("Gross profit: INR 250.00"));
		assertTrue(answer.contains("Gross margin: 25.00%"));
		assertTrue(answer.contains("Products to review: Tea (8.50% gross margin)"));
		assertTrue(answer.contains("Net profit/loss cannot be calculated"));
	}

	@Test
	void reportsGrossLossAsPositiveLossAmount() {
		when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(
			Map.of("net_revenue", new BigDecimal("400.00"), "cost_of_goods", new BigDecimal("500.00"),
				"gross_profit", new BigDecimal("-100.00"), "invoice_count", 2L),
			Map.of("invoice_count", 2L, "total_sales", new BigDecimal("400.00")));

		String answer = (String) service.ask(17L, "show gross loss this month").get("answer");

		assertTrue(answer.contains("Gross loss: INR 100.00"));
		assertTrue(answer.contains("Gross margin: -25.00%"));
	}

	@Test
	void answersTodayProfitCalculationFromRecordedFiguresWithoutCallingModel() {
		when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(
			Map.of("net_revenue", new BigDecimal("400.00"), "cost_of_goods", new BigDecimal("500.00"),
				"gross_profit", new BigDecimal("-100.00"), "invoice_count", 2L),
			Map.of("invoice_count", 2L, "total_sales", new BigDecimal("400.00")));
		when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

		String answer = (String) service.ask(17L, "today business loss and profit calculate").get("answer");

		assertTrue(answer.contains("Gross loss: INR 100.00"));
		assertTrue(answer.contains("Net profit/loss cannot be calculated"));
		verify(jdbc, org.mockito.Mockito.times(2)).queryForMap(anyString(), any(Object[].class));
	}

	@Test
	void doesNotQueryBusinessDatabaseForGeneralQuestions() {
		String greeting = (String) service.ask(17L, "Hello Nova!").get("answer");
		assertTrue(greeting.contains("sales, profit, inventory, forecasts"));
		verifyNoInteractions(jdbc);
	}

	@Test
	void usesLocalCalculationAndExplainsUnavailableReturnsWithoutDatabaseSearch() {
		String calculation = (String) service.ask(17L, "what is 25% of ₹500?").get("answer");
		assertTrue(calculation.contains("₹125"));
		String returns = (String) service.ask(17L, "show refunds today").get("answer");
		assertTrue(returns.contains("do not track returns or refunds"));
		verifyNoInteractions(jdbc);
	}
}
