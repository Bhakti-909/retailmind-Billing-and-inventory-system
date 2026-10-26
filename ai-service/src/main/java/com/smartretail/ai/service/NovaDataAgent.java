package com.smartretail.ai.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NovaDataAgent {
	private static final Pattern INVOICE_REFERENCE = Pattern.compile("\\bINV-[A-Z0-9-]+\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern ISO_DATE = Pattern.compile("\\b20\\d{2}-\\d{2}-\\d{2}\\b");
	private static final Pattern DAY_MONTH_YEAR = Pattern.compile("(?<!\\d)(\\d{1,2})[./-](\\d{1,2})[./-](20\\d{2})(?!\\d)");
	private static final String DISCOUNTED_PRODUCT_PROFIT_QUERY = """
		WITH ranked_lines AS (
		  SELECT i.id AS invoice_id, i.gst_invoice,
		         COALESCE(i.discount_amount, 0) AS discount_amount,
		         ii.product_name, ii.quantity, ii.unit_price, ii.unit_cost,
		         ii.unit_price * ii.quantity AS gross_amount,
		         COALESCE(ii.bill_discount, 0) AS gst_line_discount,
		         COALESCE(ii.taxable_amount, 0) AS gst_line_taxable,
		         COALESCE(ii.gst_amount, 0) AS gst_line_tax,
		         ROW_NUMBER() OVER (PARTITION BY i.id ORDER BY ii.id) AS line_number,
		         COUNT(*) OVER (PARTITION BY i.id) AS line_count,
		         ROUND(COALESCE(i.discount_amount, 0) * (ii.unit_price * ii.quantity)
		           / NULLIF(SUM(ii.unit_price * ii.quantity) OVER (PARTITION BY i.id), 0), 2) AS proportional_discount
		  FROM invoices i JOIN invoice_items ii ON ii.invoice_id = i.id
		  WHERE i.business_id = ? AND i.sold_at >= ? AND i.sold_at < ?
		), allocated_lines AS (
		  SELECT *, CASE WHEN gst_invoice THEN gst_line_discount
		    WHEN line_number = line_count THEN discount_amount - COALESCE(
		      SUM(proportional_discount) OVER (PARTITION BY invoice_id ORDER BY line_number
		        ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)
		    ELSE COALESCE(proportional_discount, 0) END AS line_discount
		  FROM ranked_lines
		), net_lines AS (
		  SELECT product_name, quantity,
		    CASE WHEN gst_invoice THEN gst_line_taxable + gst_line_tax ELSE gross_amount - line_discount END AS line_sales,
		    (CASE WHEN gst_invoice THEN gst_line_taxable ELSE gross_amount - line_discount END) - (unit_cost * quantity) AS line_profit
		  FROM allocated_lines
		)
		SELECT product_name, SUM(quantity) AS units_sold,
		       ROUND(SUM(line_sales)::numeric, 2) AS recorded_sales,
		       ROUND(SUM(line_profit)::numeric, 2) AS gross_profit,
		       ROUND(100 * SUM(line_profit)::numeric / NULLIF(SUM(line_sales)::numeric, 0), 2) AS gross_margin_percent
		FROM net_lines GROUP BY product_name
		""";
	private static final Set<String> QUERY_STOP_WORDS = Set.of(
		"show", "list", "find", "get", "tell", "me", "what", "which", "are", "is", "the", "a", "an", "my", "our", "for", "of", "in", "on", "from",
		"stock", "inventory", "product", "products", "price", "pricing", "details", "about", "please", "give", "latest", "recent", "available", "quantity",
		"how", "many", "do", "does", "have", "this", "these", "that", "item", "items", "customer", "customers", "bill", "bills", "invoice", "invoices",
		"sales", "sale", "business", "data", "cost", "much", "can", "could", "would", "should", "did", "who", "whom", "buy", "bought", "sold", "today", "yesterday", "week", "month", "last", "all", "forecast", "predict", "next", "future", "trend", "trends", "best", "selling",
		"and", "or", "to", "with", "their", "there", "most", "top", "spent", "spend", "purchase", "purchases", "history", "records", "record",
		"low", "out", "near", "level", "levels", "keep", "need", "needs", "suggest", "suggestion", "recommend", "recommendation",
		"named", "called", "showing", "popular", "today's", "todays", "your", "has", "was", "were",
		"report", "reports", "summary", "profit", "margin", "supplier", "suppliers", "vendor", "vendors", "earning", "earnings", "stocking",
		"assistant", "nova", "retail", "store", "shop", "businesses", "information", "know", "want", "like");
	private final JdbcTemplate jdbc;
	private final NovaForecastAgent forecastAgent;

	public NovaDataAgent(JdbcTemplate jdbc, NovaForecastAgent forecastAgent) {
		this.jdbc = jdbc;
		this.forecastAgent = forecastAgent;
	}

	public Map<String, Object> collectLiveData(Long businessId, String query,
			NovaIntentClassifier.Classification classification, Set<NovaToolRouter.Tool> tools) {
		String toolQuestion = classification.effectiveQuery();
		Map<String, Object> live = new LinkedHashMap<>();
		if (tools.contains(NovaToolRouter.Tool.BUSINESS_SNAPSHOT)) live.put("businessSnapshot", businessSnapshot(businessId));
		if (tools.contains(NovaToolRouter.Tool.INVENTORY)) live.put("inventory", inventoryTool(businessId));
		if (tools.contains(NovaToolRouter.Tool.STOCK_ADVICE)) live.put("stockAdvice", stockAdviceTool(businessId));
		if (tools.contains(NovaToolRouter.Tool.PROFIT_INSIGHTS)) {
			String profitQuestion = hasDateQualifier(query) ? query : toolQuestion;
			LocalDate[] profitRange = requestedSalesRange(profitQuestion);
			live.put("profitInsights", profitInsightsTool(businessId, profitRange[0], profitRange[1]));
		}
		if (tools.contains(NovaToolRouter.Tool.SALES_SUMMARY)) {
			String dateQuestion = hasDateQualifier(query) ? query : toolQuestion;
			live.put("selectedPeriodSales", salesSummaryTool(businessId, dateQuestion));
		}
		if (tools.contains(NovaToolRouter.Tool.DAILY_SALES_HISTORY)) {
			List<Map<String, Object>> salesHistory = salesTool(businessId);
			live.put("dailySalesLast28Days", salesHistory);
			if (tools.contains(NovaToolRouter.Tool.FORECAST_ENGINE)) live.put("forecast", forecastAgent.forecast(salesHistory));
		}
		if (tools.contains(NovaToolRouter.Tool.TOP_PRODUCTS)) {
			String productQuestion = hasDateQualifier(query) ? query : toolQuestion;
			LocalDate[] productRange = requestedSalesRange(productQuestion);
			live.put("topSellingProducts", topProductsTool(businessId, productRange[0], productRange[1]));
		}
		if (tools.contains(NovaToolRouter.Tool.PERIOD_COMPARISON)) live.put("periodComparison", periodComparisonTool(businessId, query));
		if (tools.contains(NovaToolRouter.Tool.SLOW_MOVING_PRODUCTS)) live.put("slowMovingProducts", slowMovingProductsTool(businessId));
		if (tools.contains(NovaToolRouter.Tool.PRODUCT_MATCHES) && !queryTerms(toolQuestion).isEmpty())
			live.put("matchingProducts", matchingProductsTool(businessId, toolQuestion));
		if (tools.contains(NovaToolRouter.Tool.CUSTOMER_ANALYSIS)) live.put("customerAnalysis", customerTool(businessId, toolQuestion,
			classification.intents().contains(NovaIntentClassifier.Intent.GROWTH_ANALYSIS)
				|| classification.intents().contains(NovaIntentClassifier.Intent.CUSTOMER_RETENTION)
				|| classification.intents().contains(NovaIntentClassifier.Intent.CUSTOMER_ACQUISITION)));
		if (tools.contains(NovaToolRouter.Tool.LOSS_RECOVERY_BASELINE)) live.put("lossRecoveryBaseline", lossRecoveryBaseline(businessId));
		if (tools.contains(NovaToolRouter.Tool.INVOICE_SEARCH)) live.put("recentInvoices", invoiceTool(businessId, toolQuestion));
		if (tools.contains(NovaToolRouter.Tool.SUPPLIER_ANALYSIS)) live.put("supplierAnalysis", supplierTool(businessId));
		if (tools.contains(NovaToolRouter.Tool.PRODUCT_COMBINATIONS)) live.put("frequentProductCombinations", productCombinationsTool(businessId));
		if (classification.intents().contains(NovaIntentClassifier.Intent.RETURNS))
			live.put("returnsTracking", Map.of("supported", false, "message", "The billing schema does not record returns or refunds."));
		return live;
	}

	private Map<String, Object> inventoryTool(Long businessId) {
		Map<String, Object> summary = jdbc.queryForMap("""
			SELECT COUNT(*) AS product_count,
			       COUNT(*) FILTER (WHERE quantity <= 0) AS out_of_stock_count,
			       COUNT(*) FILTER (WHERE quantity > 0 AND quantity <= reorder_level) AS low_stock_count,
			       COUNT(*) FILTER (WHERE expiry_date < CURRENT_DATE) AS expired_count,
			       COUNT(*) FILTER (WHERE expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 30) AS expiring_soon_count,
			       COUNT(*) FILTER (WHERE selling_price < cost_price) AS below_cost_count,
			       COALESCE(SUM(quantity * COALESCE(cost_price, 0)), 0) AS stock_cost_value
			FROM products WHERE business_id = ?
			""", businessId);
		List<Map<String, Object>> attention = jdbc.queryForList("""
			SELECT name, sku, quantity, reorder_level, cost_price, selling_price,
			       TO_CHAR(expiry_date, 'YYYY-MM-DD') AS expiry_date,
			       CASE WHEN quantity <= 0 THEN 'out of stock' WHEN quantity <= reorder_level THEN 'low stock'
			            WHEN expiry_date < CURRENT_DATE THEN 'expired' WHEN selling_price < cost_price THEN 'below cost' ELSE 'review' END AS issue
			FROM products WHERE business_id = ? AND (quantity <= reorder_level OR expiry_date <= CURRENT_DATE + 30 OR selling_price < cost_price)
			ORDER BY CASE WHEN quantity <= 0 THEN 0 WHEN expiry_date < CURRENT_DATE THEN 1 WHEN quantity <= reorder_level THEN 2 ELSE 3 END, name LIMIT 30
			""", businessId);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("summary", summary);
		result.put("categories", jdbc.queryForList("""
			SELECT COALESCE(NULLIF(TRIM(category), ''), 'Uncategorized') AS category,
			       COUNT(*) AS product_count, COALESCE(SUM(quantity), 0) AS units_on_hand,
			       COUNT(*) FILTER (WHERE quantity <= reorder_level) AS low_stock_count
			FROM products WHERE business_id = ?
			GROUP BY COALESCE(NULLIF(TRIM(category), ''), 'Uncategorized')
			ORDER BY product_count DESC, category LIMIT 20
			""", businessId));
		result.put("attentionProducts", attention);
		return result;
	}

	private Map<String, Object> stockAdviceTool(Long businessId) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("salesWindowDays", 28);
		result.put("targetCoverDays", 14);
		result.put("method", "Suggested quantity = max(0, recent 28-day average daily units x 14 days - current on-hand units)");
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		LocalDate from = today.minusDays(27);
		result.put("period", Map.of("from", from.toString(), "to", today.toString()));
		result.put("products", jdbc.queryForList("""
			SELECT p.name, p.sku, COALESCE(NULLIF(TRIM(p.category), ''), 'Uncategorized') AS category,
			       p.quantity AS units_on_hand, p.reorder_level,
			       COALESCE(s.units_sold_28_days, 0) AS units_sold_28_days,
			       ROUND(COALESCE(s.units_sold_28_days, 0)::numeric / 28, 2) AS average_daily_units,
			       GREATEST(0, CEIL(COALESCE(s.units_sold_28_days, 0)::numeric / 28 * 14 - p.quantity))::BIGINT AS suggested_reorder_quantity
			FROM products p
			LEFT JOIN (
			  SELECT i.business_id, LOWER(TRIM(ii.product_name)) AS product_key, SUM(ii.quantity) AS units_sold_28_days
			  FROM invoice_items ii JOIN invoices i ON i.id = ii.invoice_id
			  WHERE i.business_id = ? AND i.sold_at >= ? AND i.sold_at < ?
			  GROUP BY i.business_id, LOWER(TRIM(ii.product_name))
			) s ON s.business_id = p.business_id AND s.product_key = LOWER(TRIM(p.name))
			WHERE p.business_id = ? AND (COALESCE(s.units_sold_28_days, 0) > 0 OR p.quantity <= p.reorder_level)
			ORDER BY suggested_reorder_quantity DESC, units_sold_28_days DESC, p.name LIMIT 20
			""", businessId, from.atStartOfDay(), today.plusDays(1).atStartOfDay(), businessId));
		return result;
	}

	private Map<String, Object> profitInsightsTool(Long businessId, LocalDate from, LocalDate to) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("period", Map.of("from", from.toString(), "to", to.toString()));
		result.put("basis", "Gross profit is recorded invoice revenue minus cost of goods for this period. Product gross profit allocates non-GST bill discounts across lines and uses saved GST taxable amounts for GST bills. Operating expenses are not recorded in the available billing schema, so net profit cannot be calculated.");
		result.put("overall", jdbc.queryForMap("""
			SELECT COALESCE(SUM(revenue), 0) AS net_revenue,
			       COALESCE(SUM(cost), 0) AS cost_of_goods,
			       COALESCE(SUM(revenue - cost), 0) AS gross_profit,
			       COUNT(*) AS invoice_count
			FROM invoices WHERE business_id = ? AND sold_at >= ? AND sold_at < ?
			""", businessId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
		Object[] range = { businessId, from.atStartOfDay(), to.plusDays(1).atStartOfDay() };
		result.put("topGrossProfitProducts", jdbc.queryForList(DISCOUNTED_PRODUCT_PROFIT_QUERY + " ORDER BY gross_profit DESC, recorded_sales DESC LIMIT 5", range));
		result.put("lowestMarginProducts", jdbc.queryForList("WITH product_profit AS (" + DISCOUNTED_PRODUCT_PROFIT_QUERY + ") SELECT * FROM product_profit WHERE gross_margin_percent < 10 ORDER BY gross_margin_percent ASC LIMIT 5", range));
		return result;
	}

	private Map<String, Object> periodComparisonTool(Long businessId, String question) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		String lower = question.toLowerCase(Locale.ROOT);
		LocalDate[] current = comparisonCurrentRange(question, today);
		LocalDate[] previous = comparisonPreviousRange(lower, current);
		Map<String, Object> currentTotals = summaryForRange(businessId, current[0], current[1]);
		Map<String, Object> previousTotals = summaryForRange(businessId, previous[0], previous[1]);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("currentPeriod", Map.of("from", current[0].toString(), "to", current[1].toString()));
		result.put("previousPeriod", Map.of("from", previous[0].toString(), "to", previous[1].toString()));
		result.put("current", currentTotals);
		result.put("previous", previousTotals);
		Map<String, Object> changes = new LinkedHashMap<>();
		for (String metric : List.of("total_sales", "gross_profit", "invoice_count")) {
			java.math.BigDecimal before = decimal(previousTotals.get(metric));
			java.math.BigDecimal after = decimal(currentTotals.get(metric));
			java.math.BigDecimal difference = after.subtract(before);
			changes.put(metric, Map.of("absolute", difference, "percent", before.signum() == 0 ? "unavailable (comparison period is zero)"
				: difference.multiply(java.math.BigDecimal.valueOf(100)).divide(before.abs(), 2, java.math.RoundingMode.HALF_UP)));
		}
		result.put("changes", changes);
		result.put("basis", "Both windows use Asia/Kolkata calendar dates, inclusive start and end dates, and invoice totals/recorded revenue less cost for gross profit.");
		return result;
	}

	private LocalDate[] comparisonCurrentRange(String question, LocalDate today) {
		String lower = question.toLowerCase(Locale.ROOT);
		if (has(lower, "month over month", "month-over-month", "monthly"))
			return new LocalDate[] { today.withDayOfMonth(1), today };
		if (has(lower, "weekly", "week over week", "week-over-week"))
			return new LocalDate[] { today.minusDays(6), today };
		return requestedSalesRange(question);
	}

	private LocalDate[] comparisonPreviousRange(String question, LocalDate[] current) {
		if (has(question, "month over month", "month-over-month", "monthly", "this month")) {
			LocalDate previousMonth = current[0].minusDays(1).withDayOfMonth(1);
			int day = Math.min(current[1].getDayOfMonth(), previousMonth.lengthOfMonth());
			return new LocalDate[] { previousMonth, previousMonth.withDayOfMonth(day) };
		}
		if (has(question, "last month", "previous month")) {
			LocalDate previousMonth = current[0].minusMonths(1).withDayOfMonth(1);
			return new LocalDate[] { previousMonth, previousMonth.withDayOfMonth(previousMonth.lengthOfMonth()) };
		}
		long inclusiveDays = java.time.temporal.ChronoUnit.DAYS.between(current[0], current[1]) + 1;
		LocalDate previousTo = current[0].minusDays(1);
		return new LocalDate[] { previousTo.minusDays(inclusiveDays - 1), previousTo };
	}

	private Map<String, Object> summaryForRange(Long businessId, LocalDate from, LocalDate to) {
		return jdbc.queryForMap("""
			SELECT COUNT(*) AS invoice_count,
			       COALESCE(SUM(total_amount), 0) AS total_sales,
			       COALESCE(SUM(revenue - cost), 0) AS gross_profit
			FROM invoices WHERE business_id = ? AND sold_at >= ? AND sold_at < ?
			""", businessId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
	}

	private java.math.BigDecimal decimal(Object value) {
		return value instanceof Number number ? new java.math.BigDecimal(number.toString()) : java.math.BigDecimal.ZERO;
	}

	private Map<String, Object> slowMovingProductsTool(Long businessId) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		LocalDate from = today.minusDays(27);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("basis", "Units sold on recorded invoices in the last 28 Asia/Kolkata calendar days, joined by normalized product name. This identifies candidates to review; it does not prove why sales are low.");
		result.put("period", Map.of("from", from.toString(), "to", today.toString()));
		result.put("products", jdbc.queryForList("""
			SELECT p.name, p.sku, p.category, p.quantity AS units_on_hand, p.reorder_level,
			       COALESCE(s.units_sold, 0) AS units_sold_28_days,
			       ROUND(COALESCE(s.units_sold, 0)::numeric / 28, 2) AS average_daily_units,
			       COALESCE(p.quantity * p.cost_price, 0) AS inventory_cost_value
			FROM products p
			LEFT JOIN (
			  SELECT i.business_id, LOWER(TRIM(ii.product_name)) AS product_key, SUM(ii.quantity) AS units_sold
			  FROM invoice_items ii JOIN invoices i ON i.id = ii.invoice_id
			  WHERE i.business_id = ? AND i.sold_at >= ? AND i.sold_at < ?
			  GROUP BY i.business_id, LOWER(TRIM(ii.product_name))
			) s ON s.business_id = p.business_id AND s.product_key = LOWER(TRIM(p.name))
			WHERE p.business_id = ? AND p.quantity > 0
			ORDER BY COALESCE(s.units_sold, 0), p.quantity DESC, p.name LIMIT 20
			""", businessId, from.atStartOfDay(), today.plusDays(1).atStartOfDay(), businessId));
		return result;
	}

	private Map<String, Object> businessSnapshot(Long businessId) {
		return jdbc.queryForMap("""
			SELECT
			  (SELECT COUNT(*) FROM products WHERE business_id = ?) AS product_count,
			  (SELECT COUNT(*) FROM customers WHERE business_id = ?) AS registered_customer_count,
			  (SELECT COUNT(*) FROM suppliers WHERE business_id = ?) AS supplier_count,
			  COUNT(i.id) AS invoice_count,
			  COALESCE(SUM(i.total_amount), 0) AS lifetime_sales,
			  COALESCE(SUM(i.revenue - i.cost), 0) AS lifetime_gross_profit,
			  COALESCE(SUM(i.total_amount) FILTER (WHERE i.sold_at >= CURRENT_DATE - INTERVAL '27 days'), 0) AS sales_last_28_days,
			  MAX(i.sold_at) AS last_sale_at
			FROM (SELECT ?::BIGINT AS business_id) b
			LEFT JOIN invoices i ON i.business_id = b.business_id
			""", businessId, businessId, businessId, businessId);
	}

	private Map<String, Object> salesSummaryTool(Long businessId, String question) {
		LocalDate[] range = requestedSalesRange(question);
		LocalDate from = range[0];
		LocalDate to = range[1];
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("period", Map.of("from", from.toString(), "to", to.toString()));
		result.put("summary", jdbc.queryForMap("""
			SELECT COUNT(*) AS invoice_count,
			       COALESCE(SUM(total_amount), 0) AS total_sales,
			       COALESCE(SUM(revenue - cost), 0) AS gross_profit,
			       COALESCE(AVG(total_amount), 0) AS average_invoice,
			       COUNT(DISTINCT customer_id) AS paying_customers,
			       COALESCE(SUM(discount_amount), 0) AS discounts_given,
			       COALESCE(SUM(COALESCE(cgst_amount, 0) + COALESCE(sgst_amount, 0) + COALESCE(igst_amount, 0)), 0) AS tax_collected,
			       (SELECT COALESCE(SUM(ii.quantity), 0) FROM invoice_items ii JOIN invoices item_i ON item_i.id = ii.invoice_id
			         WHERE item_i.business_id = ? AND item_i.sold_at >= ? AND item_i.sold_at < ?) AS items_sold,
			       MAX(sold_at) AS last_sale_at
			FROM invoices
			WHERE business_id = ? AND sold_at >= ? AND sold_at < ?
			""", businessId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), businessId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
		return result;
	}

	private LocalDate[] requestedSalesRange(String question) {
		String lower = question.toLowerCase(Locale.ROOT);
		Matcher dates = ISO_DATE.matcher(question);
		List<LocalDate> explicitDates = new ArrayList<>();
		while (dates.find()) {
			try { explicitDates.add(LocalDate.parse(dates.group())); }
			catch (java.time.DateTimeException ignored) { }
		}
		Matcher dayMonthDates = DAY_MONTH_YEAR.matcher(question);
		while (dayMonthDates.find()) {
			try { explicitDates.add(LocalDate.of(Integer.parseInt(dayMonthDates.group(3)), Integer.parseInt(dayMonthDates.group(2)), Integer.parseInt(dayMonthDates.group(1)))); }
			catch (java.time.DateTimeException | NumberFormatException ignored) { }
		}
		if (!explicitDates.isEmpty()) {
			LocalDate from = explicitDates.get(0);
			LocalDate to = explicitDates.size() > 1 ? explicitDates.get(1) : from;
			return from.isAfter(to) ? new LocalDate[] { to, from } : new LocalDate[] { from, to };
		}
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		if (has(lower, "yesterday", "yestarday", "yesterady", "काल", "कल")) return new LocalDate[] { today.minusDays(1), today.minusDays(1) };
		if (has(lower, "today", "आज", "aaj")) return new LocalDate[] { today, today };
		if (has(lower, "this month", "month to date", "इस महीने", "या महिन्यात")) return new LocalDate[] { today.withDayOfMonth(1), today };
		if (has(lower, "this week", "week to date", "इस सप्ताह", "या आठवड्यात")) return new LocalDate[] { today.minusDays(today.getDayOfWeek().getValue() - 1L), today };
		if (has(lower, "last 7", "last seven", "past 7", "past seven", "pichhle 7 din", "गेल्या ७ दिवस", "मागील ७ दिवस", "पिछले ७ दिन")) return new LocalDate[] { today.minusDays(6), today };
		if (has(lower, "last 30", "last thirty", "past 30", "past thirty", "pichhle 30 din", "गेल्या ३० दिवस", "मागील ३० दिवस", "पिछले ३० दिन")) return new LocalDate[] { today.minusDays(29), today };
		if (has(lower, "past week", "past 7 days", "last 7 days", "मागील ७ दिवस")) return new LocalDate[] { today.minusDays(6), today };
		if (has(lower, "last week", "previous week", "pichhle hafte", "pichhle saptah", "गेल्या आठवड्यात", "मागील आठवडा", "मागील सप्ताह", "पिछले सप्ताह", "पिछले हफ्ते")) {
			LocalDate thisMonday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
			return new LocalDate[] { thisMonday.minusDays(7), thisMonday.minusDays(1) };
		}
		if (has(lower, "last month", "previous month", "पिछले महीने", "मागील महिना")) {
			LocalDate previousMonth = today.minusMonths(1);
			return new LocalDate[] { previousMonth.withDayOfMonth(1), previousMonth.withDayOfMonth(previousMonth.lengthOfMonth()) };
		}
		if (has(lower, "monthly", "month over month", "month-over-month")) return new LocalDate[] { today.withDayOfMonth(1), today };
		if (has(lower, "weekly", "week over week", "week-over-week")) return new LocalDate[] { today.minusDays(6), today };
		if (has(lower, "daily")) return new LocalDate[] { today, today };
		if (has(lower, "this year", "year to date", "इस साल", "या वर्षात")) return new LocalDate[] { today.withDayOfYear(1), today };
		return new LocalDate[] { today.minusDays(27), today };
	}

	private boolean hasDateQualifier(String question) {
		String lower = question.toLowerCase(Locale.ROOT);
		return ISO_DATE.matcher(question).find() || DAY_MONTH_YEAR.matcher(question).find()
			|| has(lower, "today", "daily", "weekly", "monthly", "month over month", "month-over-month", "week over week", "week-over-week", "आज", "aaj", "yesterday", "काल", "कल", "last 7", "last 30", "past 7", "past 30",
				"last week", "past week", "previous week", "last month", "previous month", "this month", "this week", "this year",
				"पिछले", "गेल्या", "मागील", "इस महीने", "इस सप्ताह", "या महिन्यात", "या आठवड्यात");
	}

	private List<Map<String, Object>> matchingProductsTool(Long businessId, String question) {
		List<String> terms = queryTerms(question);
		if (terms.isEmpty()) return List.of();
		String predicates = terms.stream()
			.map(term -> "(LOWER(name) LIKE ? OR LOWER(COALESCE(sku, '')) LIKE ? OR LOWER(COALESCE(category, '')) LIKE ?)")
			.collect(java.util.stream.Collectors.joining(" OR "));
		List<Object> args = new ArrayList<>();
		args.add(businessId);
		for (String term : terms) {
			String pattern = "%" + term + "%";
			args.add(pattern); args.add(pattern); args.add(pattern);
		}
		args.add(15);
		return jdbc.queryForList("""
			SELECT name, sku, COALESCE(category, 'Uncategorized') AS category,
			       quantity, reorder_level, selling_price, cost_price, expiry_date
			FROM products WHERE business_id = ? AND (
			""" + predicates + ") ORDER BY name LIMIT ?", args.toArray());
	}

	private List<Map<String, Object>> topProductsTool(Long businessId, LocalDate from, LocalDate to) {
		String sql = "WITH product_profit AS (" + DISCOUNTED_PRODUCT_PROFIT_QUERY + "), product_category AS (" + """
			SELECT business_id, LOWER(name) AS name_key, MAX(category) AS category
			FROM products WHERE business_id = ? GROUP BY business_id, LOWER(name)
			) SELECT product_profit.product_name,
			       COALESCE(NULLIF(TRIM(product_category.category), ''), 'Uncategorized') AS category,
			       product_profit.units_sold, product_profit.recorded_sales AS sales,
			       product_profit.gross_profit, product_profit.gross_margin_percent
			FROM product_profit LEFT JOIN product_category
			  ON product_category.business_id = ? AND product_category.name_key = LOWER(product_profit.product_name)
			ORDER BY product_profit.units_sold DESC, product_profit.recorded_sales DESC LIMIT 10
			""";
		return jdbc.queryForList(sql, businessId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), businessId, businessId);
	}

	private List<Map<String, Object>> productCombinationsTool(Long businessId) {
		return jdbc.queryForList("""
			SELECT first_item.product_name AS first_product,
			       second_item.product_name AS second_product,
			       COUNT(DISTINCT first_item.invoice_id) AS bills_together
			FROM invoice_items first_item
			JOIN invoice_items second_item ON second_item.invoice_id = first_item.invoice_id
			  AND first_item.product_name < second_item.product_name
			JOIN invoices i ON i.id = first_item.invoice_id
			WHERE i.business_id = ?
			GROUP BY first_item.product_name, second_item.product_name
			ORDER BY bills_together DESC, first_product, second_product
			LIMIT 10
			""", businessId);
	}

	private Map<String, Object> customerTool(Long businessId, String question, boolean includeRetentionMetrics) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("registeredCustomerCount", jdbc.queryForObject(
			"SELECT COUNT(*) FROM customers WHERE business_id = ?", Long.class, businessId));
		String lowerQuestion = question == null ? "" : question.toLowerCase(Locale.ROOT);
		String customerName = customerNameFromQuestion(question == null ? "" : question);
		boolean asksForTopCustomers = has(lowerQuestion, "top", "highest", "spender", "spending") || customerName != null;
		if (!includeRetentionMetrics || asksForTopCustomers) result.put("topCustomersBySpend", jdbc.queryForList("""
			SELECT COALESCE(MAX(NULLIF(TRIM(c.name), '')), MAX(NULLIF(TRIM(i.buyer_name), '')), 'Customer') AS customer_name,
			       COUNT(DISTINCT i.id) AS bill_count,
			       COALESCE(SUM(i.total_amount), 0) AS total_spend,
			       COALESCE(SUM(i.revenue - i.cost), 0) AS gross_profit,
			       MAX(i.sold_at) AS last_purchase
			FROM invoices i
			LEFT JOIN customers c ON c.id = i.customer_id AND c.business_id = i.business_id
			WHERE i.business_id = ?
			  AND NULLIF(TRIM(COALESCE(c.name, i.buyer_name)), '') IS NOT NULL
			  AND LOWER(TRIM(COALESCE(c.name, i.buyer_name))) NOT LIKE 'walk-in%'
			GROUP BY COALESCE('id:' || i.customer_id::text, 'name:' || LOWER(TRIM(i.buyer_name)))
			ORDER BY total_spend DESC, bill_count DESC
			LIMIT 10
			""", businessId));
		if (includeRetentionMetrics) {
			result.put("retentionSummary", jdbc.queryForMap("""
				WITH customer_activity AS (
				  SELECT CASE WHEN i.customer_id IS NOT NULL THEN 'id:' || i.customer_id::text
				              ELSE 'name:' || LOWER(TRIM(COALESCE(c.name, i.buyer_name))) END AS customer_key,
				         MIN(i.sold_at) AS first_purchase, MAX(i.sold_at) AS last_purchase, COUNT(*) AS bill_count
				  FROM invoices i LEFT JOIN customers c ON c.id = i.customer_id AND c.business_id = i.business_id
				  WHERE i.business_id = ? AND i.sold_at >= CURRENT_DATE - INTERVAL '89 days'
				    AND NULLIF(TRIM(COALESCE(c.name, i.buyer_name, '')), '') IS NOT NULL
				    AND LOWER(TRIM(COALESCE(c.name, i.buyer_name, ''))) NOT LIKE 'walk-in%'
				  GROUP BY 1
				)
				SELECT COUNT(*) AS customers_with_invoices_90d,
				       COUNT(*) FILTER (WHERE bill_count >= 2) AS repeat_customers_90d,
				       COUNT(*) FILTER (WHERE first_purchase >= CURRENT_DATE - INTERVAL '29 days') AS new_customers_30d,
				       COUNT(*) FILTER (WHERE last_purchase >= CURRENT_DATE - INTERVAL '29 days') AS active_customers_30d,
				       COUNT(*) FILTER (WHERE last_purchase < CURRENT_DATE - INTERVAL '30 days') AS lapsed_customers_90d
			FROM customer_activity
			""", businessId));
		}
		if (customerName != null) {
			result.put("matchingCustomers", jdbc.queryForList("""
				SELECT COALESCE(NULLIF(TRIM(c.name), ''), NULLIF(TRIM(i.buyer_name), ''), 'Customer') AS customer_name,
				       COUNT(DISTINCT i.id) AS bill_count,
				       COALESCE(SUM(i.total_amount), 0) AS total_spend,
				       MAX(i.sold_at) AS last_purchase
				FROM invoices i
				LEFT JOIN customers c ON c.id = i.customer_id AND c.business_id = i.business_id
				WHERE i.business_id = ? AND LOWER(COALESCE(c.name, i.buyer_name, '')) LIKE ?
				GROUP BY i.customer_id, c.name, i.buyer_name ORDER BY last_purchase DESC LIMIT 5
				""", businessId, "%" + customerName.toLowerCase(Locale.ROOT) + "%"));
		}
		return result;
	}

	private Map<String, Object> lossRecoveryBaseline(Long businessId) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		LocalDate from = today.minusDays(27);
		Map<String, Object> baseline = jdbc.queryForMap("""
			SELECT COALESCE(SUM(revenue), 0) AS net_revenue_28d,
			       COALESCE(SUM(revenue - cost), 0) AS gross_profit_28d,
			       COUNT(*) AS invoice_count_28d
			FROM invoices WHERE business_id = ? AND sold_at >= ? AND sold_at < ?
			""", businessId, from.atStartOfDay(), today.plusDays(1).atStartOfDay());
		Map<String, Object> result = new LinkedHashMap<>(baseline);
		result.put("period", Map.of("from", from.toString(), "to", today.toString()));
		result.put("basis", "Observed saved invoice revenue and cost over the latest 28 business dates; this is a gross-profit baseline and excludes unrecorded operating expenses.");
		return result;
	}

	private List<Map<String, Object>> invoiceTool(Long businessId, String question) {
		Matcher reference = INVOICE_REFERENCE.matcher(question);
		String invoiceNumber = reference.find() ? reference.group() : null;
		String customerName = customerNameFromQuestion(question);
		boolean searchCustomer = invoiceNumber == null && customerName != null;
		String referenceFilter = invoiceNumber != null ? " AND i.invoice_number ILIKE ?"
			: searchCustomer ? " AND LOWER(COALESCE(c.name, i.buyer_name, '')) LIKE ?" : "";
		String sql = """
			SELECT i.invoice_number,
			       TO_CHAR(i.sold_at, 'YYYY-MM-DD HH24:MI') AS sold_at,
			       COALESCE(NULLIF(TRIM(c.name), ''), NULLIF(TRIM(i.buyer_name), ''), 'Walk-in customer') AS customer_name,
			       i.total_amount, i.discount_amount, i.taxable_amount,
			       i.cgst_amount, i.sgst_amount, i.igst_amount,
			       COALESCE(STRING_AGG(DISTINCT ii.product_name || ' x ' || ii.quantity, ', '), 'No line items') AS items
			FROM invoices i
			LEFT JOIN customers c ON c.id = i.customer_id AND c.business_id = i.business_id
			LEFT JOIN invoice_items ii ON ii.invoice_id = i.id
			WHERE i.business_id = ?
			""" + referenceFilter + """
			GROUP BY i.id, c.name
			ORDER BY i.sold_at DESC, i.id DESC
			LIMIT ?
			""";
		if (invoiceNumber != null) return jdbc.queryForList(sql, businessId, invoiceNumber, 1);
		if (searchCustomer) return jdbc.queryForList(sql, businessId, "%" + customerName.toLowerCase(Locale.ROOT) + "%", 10);
		return jdbc.queryForList(sql, businessId, 10);
	}

	private List<Map<String, Object>> supplierTool(Long businessId) {
		return jdbc.queryForList("""
			SELECT s.name AS supplier_name,
			       COUNT(DISTINCT p.id) AS linked_product_count,
			       COALESCE(SUM(p.quantity), 0) AS linked_units_on_hand,
			       COUNT(DISTINCT p.id) FILTER (WHERE p.quantity <= p.reorder_level) AS low_stock_products
			FROM suppliers s
			LEFT JOIN products p ON p.business_id = s.business_id
			  AND LOWER(TRIM(p.supplier_name)) = LOWER(TRIM(s.name))
			WHERE s.business_id = ?
			GROUP BY s.id, s.name
			ORDER BY linked_product_count DESC, s.name
			LIMIT 20
			""", businessId);
	}

	private List<String> queryTerms(String question) {
		return java.util.Arrays.stream(question.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s-]", " ").split("\\s+"))
			.filter(term -> term.length() > 2 && !QUERY_STOP_WORDS.contains(term))
			.distinct().limit(6).toList();
	}

	private String customerNameFromQuestion(String question) {
		String lower = question.toLowerCase(Locale.ROOT);
		String[] markers = { "customer named ", "customer called ", "buyer named ", "buyer called ", "customer ", "buyer ", "for " };
		for (String marker : markers) {
			int start = lower.lastIndexOf(marker);
			if (start < 0) continue;
			String name = question.substring(start + marker.length()).trim()
				.replaceFirst("(?i)^(customer|buyer)\\s+", "")
				.replaceFirst("(?i)\\s+(today|yesterday|this week|last week|this month|last month|recent|history|purchase|purchases|buy|bought|spend|spent|bill|bills|invoice|invoices|sale|sales|details|record|records).*?$", "")
				.replaceFirst("'s$", "").replaceFirst("[?!.].*$", "").trim();
			String normalized = name.toLowerCase(Locale.ROOT);
			String firstWord = normalized.split("\\s+", 2)[0];
			if (name.isBlank() || QUERY_STOP_WORDS.contains(firstWord)
				|| Set.of("all customers", "all buyers", "top customers", "everyone", "anyone").contains(normalized)) return null;
			return name;
		}
		if (has(lower, "buy", "bought", "purchased", "purchase")) {
			List<String> terms = queryTerms(question);
			if (!terms.isEmpty()) return String.join(" ", terms);
		}
		return null;
	}

	private boolean has(String question, String... words) {
		for (String word : words) if (question.contains(word)) return true;
		return false;
	}

	private List<Map<String, Object>> salesTool(Long businessId) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		LocalDate from = today.minusDays(27);
		return jdbc.queryForList("""
			SELECT TO_CHAR(day::date, 'YYYY-MM-DD') AS date,
			       COUNT(i.id) AS bills, COALESCE(SUM(i.total_amount), 0) AS sales,
			       COALESCE(SUM(i.revenue - i.cost), 0) AS gross_profit
			FROM GENERATE_SERIES(?::date, ?::date, INTERVAL '1 day') AS day
			LEFT JOIN invoices i ON i.business_id = ? AND i.sold_at >= day::date AND i.sold_at < day::date + INTERVAL '1 day'
			GROUP BY day::date ORDER BY day::date
			""", from, today, businessId);
	}

}
