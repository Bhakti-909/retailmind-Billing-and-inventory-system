package com.smartretail.ai.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

@Component
public class ForecastEngine {
	public Map<String, Object> forecast(List<Map<String, Object>> dailySales) {
		int n = dailySales.size();
		double latestWeek = average(dailySales, Math.max(0, n - 7), n);
		double priorWeek = average(dailySales, Math.max(0, n - 14), Math.max(0, n - 7));
		double trend = priorWeek > 0 ? (latestWeek - priorWeek) / priorWeek : 0;
		double projectedDaily = Math.max(0, latestWeek + (latestWeek - priorWeek) * 0.5);
		LocalDate start = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).plusDays(1);
		Map<DayOfWeek, Double> weekdayTotals = new java.util.EnumMap<>(DayOfWeek.class);
		Map<DayOfWeek, Integer> weekdayCounts = new java.util.EnumMap<>(DayOfWeek.class);
		for (Map<String, Object> row : dailySales) {
			Object dateValue = row.get("date");
			if (dateValue == null) continue;
			DayOfWeek weekday = LocalDate.parse(dateValue.toString()).getDayOfWeek();
			weekdayTotals.merge(weekday, number(row.get("sales")), Double::sum);
			weekdayCounts.merge(weekday, 1, Integer::sum);
		}
		double seasonalTotal = 0;
		double overallMean = average(dailySales, 0, n);
		List<Map<String, Object>> dailyForecast = new java.util.ArrayList<>();
		for (int day = 0; day < 7; day++) {
			LocalDate forecastDate = start.plusDays(day);
			DayOfWeek weekday = forecastDate.getDayOfWeek();
			double weekdayMean = weekdayTotals.getOrDefault(weekday, 0.0) / Math.max(1, weekdayCounts.getOrDefault(weekday, 0));
			double factor = overallMean > 0 && weekdayMean > 0 ? weekdayMean / overallMean : 1;
			double estimate = round(projectedDaily * factor);
			seasonalTotal += estimate;
			dailyForecast.add(Map.of("date", forecastDate.toString(), "weekday", weekday.toString(), "estimatedSales", estimate));
		}
		return Map.of("historyDays", n, "latestWeekDailyAverage", round(latestWeek), "previousWeekDailyAverage", round(priorWeek),
			"weekOverWeekChangePercent", round(trend * 100), "nextSevenDaySalesEstimate", round(seasonalTotal),
			"forecastStart", start.toString(), "forecastEnd", start.plusDays(6).toString(),
			"seasonalityMethod", "weekday averages across the 28-day sales history", "dailyForecast", dailyForecast);
	}
	private double average(List<Map<String, Object>> rows, int from, int to) {
		if (to <= from) return 0;
		double sum = 0;
		for (int i = from; i < to; i++) sum += number(rows.get(i).get("sales"));
		return sum / (to - from);
	}
	private double number(Object value) { return value instanceof Number n ? n.doubleValue() : 0; }
	private double round(double value) { return Math.round(value * 100.0) / 100.0; }
}
