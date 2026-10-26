package com.smartretail.ai.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NovaLossRecoveryPlannerTest {
	private final NovaLossRecoveryPlanner planner = new NovaLossRecoveryPlanner();

	@Test
	void computesSevenDayTargetsFromGrossLossAndPositiveHistoricalMargin() {
		Map<String, Object> live = Map.of(
			"profitInsights", Map.of("period", Map.of("from", "2026-10-12", "to", "2026-10-12"),
				"overall", Map.of("gross_profit", new BigDecimal("-700.00"))),
			"lossRecoveryBaseline", Map.of("net_revenue_28d", new BigDecimal("28000.00"),
				"gross_profit_28d", new BigDecimal("7000.00"), "period", Map.of("from", "2026-09-15", "to", "2026-10-12")));

		Map<String, Object> plan = planner.buildPlan(live, planner.recoveryDays("cover today's loss within 7 days"));

		assertEquals("gross_loss_recovery_target", plan.get("status"));
		assertEquals(new BigDecimal("700.00"), plan.get("recordedGrossLoss"));
		assertEquals(new BigDecimal("100.00"), plan.get("requiredAverageDailyGrossProfit"));
		assertEquals(new BigDecimal("25.00"), plan.get("historicalGrossMarginPercent28d"));
		assertEquals(new BigDecimal("400.00"), plan.get("estimatedDailyRevenueAtHistoricalGrossMargin"));
		assertTrue(String.valueOf(plan.get("basis")).contains("gross profit"));
	}

	@Test
	void doesNotInventRevenueTargetWhenHistoricalMarginIsNotPositive() {
		Map<String, Object> live = Map.of(
			"profitInsights", Map.of("overall", Map.of("gross_profit", new BigDecimal("-100.00"))),
			"lossRecoveryBaseline", Map.of("net_revenue_28d", new BigDecimal("2000.00"), "gross_profit_28d", BigDecimal.ZERO));

		Map<String, Object> plan = planner.buildPlan(live, 7);

		assertEquals("unavailable", plan.get("historicalGrossMarginPercent28d"));
		assertTrue(!plan.containsKey("estimatedDailyRevenueAtHistoricalGrossMargin"));
	}

	@Test
	void explainsWhenNoRecordedGrossLossExists() {
		Map<String, Object> live = Map.of("profitInsights", Map.of("overall", Map.of("gross_profit", new BigDecimal("15.00"))));
		Map<String, Object> plan = planner.buildPlan(live, 7);
		assertEquals("no_recorded_gross_loss", plan.get("status"));
		assertTrue(String.valueOf(plan.get("message")).contains("Operating expenses are unavailable"));
	}
}
