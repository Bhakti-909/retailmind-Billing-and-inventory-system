package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds transparent gross-profit recovery targets from tenant-scoped invoice data. */
@Component
public class NovaLossRecoveryPlanner {
	private static final Pattern DAY_HORIZON = Pattern.compile("(?i)\\b(?:within|in|over|next)\\s+(\\d{1,2})\\s+days?\\b");

	public int recoveryDays(String question) {
		Matcher matcher = DAY_HORIZON.matcher(question == null ? "" : question);
		if (matcher.find()) return Math.max(1, Math.min(Integer.parseInt(matcher.group(1)), 90));
		return 7;
	}

	public Map<String, Object> buildPlan(Map<String, Object> live, int requestedDays) {
		int days = Math.max(1, Math.min(requestedDays, 90));
		Map<String, Object> insights = map(live.get("profitInsights"));
		Map<String, Object> overall = map(insights.get("overall"));
		Map<String, Object> baseline = map(live.get("lossRecoveryBaseline"));
		BigDecimal periodGrossProfit = decimal(overall.get("gross_profit"));
		Map<String, Object> plan = new LinkedHashMap<>();
		plan.put("basis", "Targets use recorded gross profit (invoice revenue minus cost of goods). They do not represent net profit because operating expenses are not recorded.");
		plan.put("recoveryDays", days);
		plan.put("lossPeriod", insights.getOrDefault("period", Map.of()));
		if (overall.isEmpty()) {
			plan.put("status", "insufficient_data");
			plan.put("message", "Recorded gross-profit figures are unavailable, so a recovery target cannot be calculated.");
			return plan;
		}
		if (periodGrossProfit.signum() >= 0) {
			plan.put("status", "no_recorded_gross_loss");
			plan.put("recordedGrossProfit", periodGrossProfit);
			plan.put("message", "The selected records do not show a gross loss. Operating expenses are unavailable, so an operating or net loss cannot be ruled out.");
			return plan;
		}

		BigDecimal loss = periodGrossProfit.abs();
		BigDecimal dailyTarget = loss.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
		BigDecimal historicalRevenue = decimal(baseline.get("net_revenue_28d"));
		BigDecimal historicalGrossProfit = decimal(baseline.get("gross_profit_28d"));
		BigDecimal historicalAverageDailyGrossProfit = historicalGrossProfit.divide(BigDecimal.valueOf(28), 2, RoundingMode.HALF_UP);
		plan.put("status", "gross_loss_recovery_target");
		plan.put("recordedGrossLoss", loss);
		plan.put("requiredGrossProfitOverRecoveryPeriod", loss);
		plan.put("requiredAverageDailyGrossProfit", dailyTarget);
		plan.put("historicalAverageDailyGrossProfit28d", historicalAverageDailyGrossProfit);
		plan.put("additionalDailyGrossProfitVsHistoricalAverage", dailyTarget.subtract(historicalAverageDailyGrossProfit).max(BigDecimal.ZERO));
		plan.put("baselinePeriod", baseline.getOrDefault("period", Map.of()));
		if (historicalRevenue.signum() > 0 && historicalGrossProfit.signum() > 0) {
			BigDecimal margin = historicalGrossProfit.multiply(BigDecimal.valueOf(100))
				.divide(historicalRevenue, 2, RoundingMode.HALF_UP);
			plan.put("historicalGrossMarginPercent28d", margin);
			plan.put("estimatedDailyRevenueAtHistoricalGrossMargin", dailyTarget.multiply(BigDecimal.valueOf(100))
				.divide(margin, 2, RoundingMode.HALF_UP));
			plan.put("revenueTargetBasis", "Illustrative revenue equivalent using the positive recorded 28-day gross margin; gross profit remains the recovery target.");
		} else {
			plan.put("historicalGrossMarginPercent28d", "unavailable");
			plan.put("revenueTargetBasis", "No positive historical gross margin is available, so no revenue target is estimated. Focus on profitable transactions and verify saved costs and discounts.");
		}
		return plan;
	}

	private Map<String, Object> map(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> result = new LinkedHashMap<>();
			map.forEach((key, item) -> { if (key instanceof String text) result.put(text, item); });
			return result;
		}
		return Map.of();
	}

	private BigDecimal decimal(Object value) {
		if (value instanceof BigDecimal decimal) return decimal;
		if (value instanceof Number number) return new BigDecimal(number.toString());
		return BigDecimal.ZERO;
	}
}
