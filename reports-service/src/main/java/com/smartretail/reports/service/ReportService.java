package com.smartretail.reports.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {
	private static final String RANGED_LINES_CTE = """
			WITH ranked_lines AS (
				SELECT i.id AS invoice_id,
				       i.gst_invoice,
				       COALESCE(i.discount_amount, 0) AS discount_amount,
				       ii.id AS line_id,
				       ii.product_name,
				       ii.quantity,
				       ii.unit_price,
				       ii.unit_cost,
				       COALESCE(ii.mrp_price, ii.unit_price) AS mrp_price,
				       COALESCE(ii.product_discount, 0) AS product_discount,
				       COALESCE(ii.gst_rate, 0) AS gst_rate,
				       ii.unit_price * ii.quantity AS gross_amount,
				       ii.unit_cost * ii.quantity AS cost_amount,
				       COALESCE(ii.bill_discount, 0) AS gst_line_discount,
				       COALESCE(ii.taxable_amount, 0) AS gst_line_taxable,
				       COALESCE(ii.gst_amount, 0) AS gst_line_tax,
				       ROW_NUMBER() OVER (PARTITION BY i.id ORDER BY ii.id) AS line_number,
				       COUNT(*) OVER (PARTITION BY i.id) AS line_count,
				       ROUND(COALESCE(i.discount_amount, 0) * (ii.unit_price * ii.quantity)
				             / NULLIF(SUM(ii.unit_price * ii.quantity) OVER (PARTITION BY i.id), 0), 2) AS proportional_discount
				FROM invoices i
				JOIN invoice_items ii ON ii.invoice_id = i.id
				WHERE i.business_id = ? AND i.sold_at >= ? AND i.sold_at < ?
			), allocated_lines AS (
				SELECT *,
				       CASE
				         WHEN gst_invoice THEN gst_line_discount
				         WHEN line_number = line_count THEN discount_amount - COALESCE(
				           SUM(proportional_discount) OVER (
				             PARTITION BY invoice_id ORDER BY line_number
				             ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)
				         ELSE COALESCE(proportional_discount, 0)
				       END AS line_discount
				FROM ranked_lines
			), net_lines AS (
				SELECT invoice_id, line_id, product_name, quantity, unit_price, unit_cost,
				       mrp_price, product_discount, gst_rate, line_discount,
				       CASE WHEN gst_invoice THEN gst_line_taxable ELSE gross_amount - line_discount END AS taxable_amount,
				       gst_line_tax AS gst_amount,
				       cost_amount,
				       CASE WHEN gst_invoice THEN gst_line_taxable + gst_line_tax
				            ELSE gross_amount - line_discount END AS line_sales,
				       CASE WHEN gst_invoice THEN gst_line_taxable
				            ELSE gross_amount - line_discount END - cost_amount AS line_profit
				FROM allocated_lines
			)
			""";

	private final JdbcTemplate jdbc;

	public ReportService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Map<String, Object> overview(Long businessId, LocalDate from, LocalDate to) {
		Map<String, Object> result = range(from, to);
		result.put("summary", salesSummary(businessId, from, to));
		result.put("dailySales", dailySales(businessId, from, to));
		result.put("topProducts", topProducts(businessId, from, to));
		return result;
	}

	public Map<String, Object> sales(Long businessId, LocalDate from, LocalDate to) {
		Map<String, Object> result = range(from, to);
		result.put("summary", salesSummary(businessId, from, to));
		result.put("dailySales", dailySales(businessId, from, to));
		result.put("topProducts", topProducts(businessId, from, to));
		return result;
	}

	public Map<String, Object> inventory(Long businessId) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("summary", jdbc.queryForMap("""
				SELECT COUNT(*) AS product_count,
				       COALESCE(SUM(quantity), 0) AS units_on_hand,
			       COUNT(*) FILTER (WHERE quantity <= reorder_level) AS low_stock_count,
			       COUNT(*) FILTER (WHERE quantity <= 0) AS out_of_stock_count,
			       COUNT(*) FILTER (WHERE expiry_date < CURRENT_DATE) AS expired_count,
			       COALESCE(SUM(quantity * COALESCE(cost_price, 0)), 0) AS cost_value,
				       COALESCE(SUM(quantity * COALESCE(selling_price, 0)), 0) AS retail_value,
				       COUNT(*) FILTER (WHERE expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 30) AS expiring_soon_count
				FROM products WHERE business_id = ?
				""", businessId));
		result.put("categories", jdbc.queryForList("""
				SELECT COALESCE(NULLIF(TRIM(category), ''), 'Uncategorized') AS category,
				       COUNT(*) AS product_count,
				       COALESCE(SUM(quantity), 0) AS units_on_hand,
				       COALESCE(SUM(quantity * COALESCE(cost_price, 0)), 0) AS cost_value
				FROM products WHERE business_id = ?
				GROUP BY COALESCE(NULLIF(TRIM(category), ''), 'Uncategorized')
				ORDER BY cost_value DESC, category ASC
			""", businessId));
		result.put("suppliers", jdbc.queryForList("""
				SELECT COALESCE(NULLIF(TRIM(supplier_name), ''), 'Unassigned') AS supplier_name,
			       COUNT(*) AS product_count,
			       COALESCE(SUM(quantity), 0) AS units_on_hand,
			       COALESCE(SUM(quantity * COALESCE(cost_price, 0)), 0) AS cost_value,
			       COUNT(*) FILTER (WHERE quantity <= reorder_level) AS low_stock_count
			FROM products WHERE business_id = ?
			GROUP BY COALESCE(NULLIF(TRIM(supplier_name), ''), 'Unassigned')
			ORDER BY cost_value DESC, supplier_name ASC
			""", businessId));
		result.put("attentionItems", jdbc.queryForList("""
				SELECT id, name, sku, COALESCE(category, 'Uncategorized') AS category,
			       quantity, reorder_level AS reorder_level,
			       COALESCE(cost_price, 0) AS cost_price,
			       TO_CHAR(expiry_date, 'YYYY-MM-DD') AS expiry_date
			FROM products
			WHERE business_id = ?
			  AND (quantity <= reorder_level OR expiry_date <= CURRENT_DATE + 30)
			ORDER BY CASE WHEN expiry_date < CURRENT_DATE THEN 0 WHEN quantity <= 0 THEN 1 WHEN quantity <= reorder_level THEN 2 ELSE 3 END,
			         quantity ASC, expiry_date ASC NULLS LAST, name ASC
			LIMIT 20
			""", businessId));
		return result;
	}

	public Map<String, Object> tax(Long businessId, LocalDate from, LocalDate to) {
		Map<String, Object> result = range(from, to);
		result.put("summary", jdbc.queryForMap("""
				SELECT COUNT(*) AS invoice_count,
				       COALESCE(SUM(taxable_amount), 0) AS taxable_sales,
				       COALESCE(SUM(cgst_amount), 0) AS cgst,
				       COALESCE(SUM(sgst_amount), 0) AS sgst,
				       COALESCE(SUM(igst_amount), 0) AS igst,
			       COALESCE(SUM(COALESCE(cgst_amount, 0) + COALESCE(sgst_amount, 0) + COALESCE(igst_amount, 0)), 0) AS tax_collected,
				       COALESCE(SUM(total_amount), 0) AS invoice_total
				FROM invoices
				WHERE business_id = ? AND gst_invoice = TRUE AND sold_at >= ? AND sold_at < ?
				""", businessId, start(from), endExclusive(to)));
		result.put("rates", jdbc.queryForList("""
				SELECT COALESCE(ii.gst_rate, 0) AS gst_rate,
				       COALESCE(SUM(ii.taxable_amount), 0) AS taxable_sales,
				       COALESCE(SUM(ii.gst_amount), 0) AS tax_collected,
				       COALESCE(SUM(ii.quantity), 0) AS units_sold
			FROM invoice_items ii
			JOIN invoices i ON i.id = ii.invoice_id
			WHERE i.business_id = ? AND i.gst_invoice = TRUE AND i.sold_at >= ? AND i.sold_at < ?
			GROUP BY COALESCE(ii.gst_rate, 0)
			ORDER BY gst_rate ASC
			""", businessId, start(from), endExclusive(to)));
		return result;
	}

	public Map<String, Object> customers(Long businessId, LocalDate from, LocalDate to) {
		Map<String, Object> result = range(from, to);
		result.put("summary", jdbc.queryForMap("""
				SELECT COUNT(DISTINCT COALESCE('id:' || customer_id::text,
				                              'name:' || LOWER(TRIM(buyer_name)))) AS customer_count,
			       COUNT(*) AS bill_count,
			       COALESCE(SUM(total_amount), 0) AS customer_sales,
			       COALESCE(AVG(total_amount), 0) AS average_bill
			FROM invoices
			WHERE business_id = ? AND sold_at >= ? AND sold_at < ?
			  AND NULLIF(TRIM(buyer_name), '') IS NOT NULL
			  AND LOWER(TRIM(buyer_name)) NOT LIKE 'walk-in%'
			""", businessId, start(from), endExclusive(to)));
		result.put("topCustomers", jdbc.queryForList("""
				SELECT COALESCE(c.id, i.customer_id) AS customer_id,
			       MAX(COALESCE(c.name, i.buyer_name)) AS customer_name,
			       MAX(COALESCE(c.phone, '')) AS phone,
			       COUNT(*) AS bill_count,
			       COALESCE(SUM(i.total_amount), 0) AS total_spend,
			       COALESCE(SUM(i.revenue - i.cost), 0) AS gross_profit,
			       TO_CHAR(MAX(i.sold_at), 'YYYY-MM-DD"T"HH24:MI:SS') || '+05:30' AS last_purchase
			FROM invoices i
			LEFT JOIN customers c ON c.id = i.customer_id AND c.business_id = i.business_id
			WHERE i.business_id = ? AND i.sold_at >= ? AND i.sold_at < ?
			  AND NULLIF(TRIM(i.buyer_name), '') IS NOT NULL
			  AND LOWER(TRIM(i.buyer_name)) NOT LIKE 'walk-in%'
			GROUP BY COALESCE('id:' || i.customer_id::text,
			                  'name:' || LOWER(TRIM(i.buyer_name))), COALESCE(c.id, i.customer_id)
			ORDER BY total_spend DESC, bill_count DESC
			LIMIT 20
			""", businessId, start(from), endExclusive(to)));
		return result;
	}

	public Map<String, Object> invoices(Long businessId, LocalDate from, LocalDate to, int page, int size, String search, boolean allDates) {
		String term = search == null || search.isBlank() ? null : search.trim();
		long offset = (long) page * size;
		Map<String, Object> result = range(from, to);
		Long total = jdbc.queryForObject("""
				SELECT COUNT(*)
				FROM invoices i
				LEFT JOIN customers c ON c.id = i.customer_id AND c.business_id = i.business_id
				WHERE i.business_id = ? AND (? = TRUE OR (i.sold_at >= ? AND i.sold_at < ?))
				  AND (COALESCE(?, '') = ''
				       OR COALESCE(i.invoice_number, '') ILIKE '%' || ? || '%'
				       OR COALESCE(i.buyer_name, '') ILIKE '%' || ? || '%'
				       OR COALESCE(c.name, '') ILIKE '%' || ? || '%'
				       OR COALESCE(c.phone, '') ILIKE '%' || ? || '%')
				""", Long.class, businessId, allDates, start(from), endExclusive(to), term, term, term, term, term);
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT i.id AS invoice_id,
				       i.invoice_number,
				       TO_CHAR(i.sold_at, 'YYYY-MM-DD"T"HH24:MI:SS') || '+05:30' AS sold_at,
				       COALESCE(i.seller_gstin, '') AS seller_gstin,
				       COALESCE(i.buyer_gstin, '') AS buyer_gstin,
				       COALESCE(i.gst_rate, 0) AS gst_rate,
				       COALESCE(NULLIF(TRIM(c.name), ''), NULLIF(TRIM(i.buyer_name), ''), 'Walk-in customer') AS customer_name,
				       COALESCE(c.phone, '') AS customer_phone,
				       i.customer_id,
				       COUNT(ii.id) AS item_count,
				       COALESCE(i.gst_invoice, FALSE) AS gst_invoice,
				       COALESCE(i.discount_amount, 0) AS discount_amount,
				       COALESCE(i.taxable_amount, i.revenue, 0) AS taxable_amount,
				       COALESCE(i.cgst_amount, 0) AS cgst_amount,
				       COALESCE(i.sgst_amount, 0) AS sgst_amount,
				       COALESCE(i.igst_amount, 0) AS igst_amount,
				       COALESCE(i.total_amount, i.revenue, 0) AS total_amount,
				       COALESCE(i.revenue, 0) AS net_revenue,
				       COALESCE(i.cost, 0) AS cost_of_goods,
				       COALESCE(i.revenue, 0) - COALESCE(i.cost, 0) AS gross_profit
				FROM invoices i
				LEFT JOIN customers c ON c.id = i.customer_id AND c.business_id = i.business_id
				LEFT JOIN invoice_items ii ON ii.invoice_id = i.id
				WHERE i.business_id = ? AND (? = TRUE OR (i.sold_at >= ? AND i.sold_at < ?))
				  AND (COALESCE(?, '') = ''
				       OR COALESCE(i.invoice_number, '') ILIKE '%' || ? || '%'
				       OR COALESCE(i.buyer_name, '') ILIKE '%' || ? || '%'
				       OR COALESCE(c.name, '') ILIKE '%' || ? || '%'
				       OR COALESCE(c.phone, '') ILIKE '%' || ? || '%')
				GROUP BY i.id, c.name, c.phone
				ORDER BY i.sold_at DESC, i.id DESC
				LIMIT ? OFFSET ?
				""", businessId, allDates, start(from), endExclusive(to), term, term, term, term, term, size, offset);
		result.put("invoices", rows);
		result.put("page", page);
		result.put("size", size);
		result.put("totalElements", total == null ? 0 : total);
		result.put("totalPages", total == null || total == 0 ? 0 : (total + size - 1) / size);
		result.put("search", term == null ? "" : term);
		return result;
	}

	public List<Map<String, Object>> invoiceItems(Long businessId, Long invoiceId, LocalDate from, LocalDate to, boolean allDates) {
		String linesCte = allDates ? RANGED_LINES_CTE.replace("AND i.sold_at >= ? AND i.sold_at < ?", "") : RANGED_LINES_CTE;
		String sql = linesCte + """
				SELECT line_id, product_name, quantity, unit_price, unit_cost,
				       mrp_price, product_discount, line_discount AS bill_discount, gst_rate,
				       taxable_amount, gst_amount, line_sales, line_profit
				FROM net_lines
				WHERE invoice_id = ?
				ORDER BY line_id ASC
				""";
		return allDates ? jdbc.queryForList(sql, businessId, invoiceId)
				: jdbc.queryForList(sql, businessId, start(from), endExclusive(to), invoiceId);
	}

	public Map<String, Object> productCombinations(Long businessId, LocalDate from, LocalDate to) {
		Map<String, Object> result = range(from, to);
		result.put("combinations", jdbc.queryForList(RANGED_LINES_CTE + """
				SELECT a.product_name AS product_a,
			       b.product_name AS product_b,
			       COUNT(*) AS bills_together,
			       COALESCE(SUM(a.units + b.units), 0) AS combined_units,
			       COALESCE(SUM(a.sales + b.sales), 0) AS combined_sales
			FROM (SELECT invoice_id, product_name, SUM(quantity) AS units, SUM(line_sales) AS sales
			      FROM net_lines GROUP BY invoice_id, product_name) a
			JOIN (SELECT invoice_id, product_name, SUM(quantity) AS units, SUM(line_sales) AS sales
			      FROM net_lines GROUP BY invoice_id, product_name) b
			  ON b.invoice_id = a.invoice_id AND a.product_name < b.product_name
			GROUP BY a.product_name, b.product_name
			ORDER BY bills_together DESC, combined_sales DESC
			LIMIT 10
			""", businessId, start(from), endExclusive(to)));
		return result;
	}

	private Map<String, Object> salesSummary(Long businessId, LocalDate from, LocalDate to) {
		return jdbc.queryForMap("""
				SELECT COUNT(*) AS bill_count,
			       COALESCE(SUM(total_amount), 0) AS total_sales,
			       COALESCE(SUM(revenue), 0) AS net_revenue,
			       COALESCE(SUM(cost), 0) AS cost_of_goods,
			       COALESCE(SUM(revenue - cost), 0) AS gross_profit,
			       COALESCE(AVG(total_amount), 0) AS average_bill,
			       COALESCE(SUM(discount_amount), 0) AS bill_discounts,
			       COALESCE(SUM(COALESCE(cgst_amount, 0) + COALESCE(sgst_amount, 0) + COALESCE(igst_amount, 0)), 0) AS tax_collected
			FROM invoices WHERE business_id = ? AND sold_at >= ? AND sold_at < ?
			""", businessId, start(from), endExclusive(to));
	}

	private List<Map<String, Object>> dailySales(Long businessId, LocalDate from, LocalDate to) {
		return jdbc.queryForList("""
				SELECT TO_CHAR(sold_at::date, 'YYYY-MM-DD') AS date,
			       COUNT(*) AS bill_count,
			       COALESCE(SUM(total_amount), 0) AS total_sales,
			       COALESCE(SUM(revenue - cost), 0) AS gross_profit
			FROM invoices WHERE business_id = ? AND sold_at >= ? AND sold_at < ?
			GROUP BY sold_at::date ORDER BY sold_at::date ASC
			""", businessId, start(from), endExclusive(to));
	}

	private List<Map<String, Object>> topProducts(Long businessId, LocalDate from, LocalDate to) {
		return jdbc.queryForList(RANGED_LINES_CTE + """
				SELECT ii.product_name,
			       COALESCE(SUM(quantity), 0) AS units_sold,
			       COALESCE(SUM(line_sales), 0) AS sales,
			       COALESCE(SUM(line_profit), 0) AS gross_profit
			FROM net_lines ii
			JOIN products p
			  ON p.business_id = ?
			 AND LOWER(BTRIM(p.name)) = LOWER(BTRIM(ii.product_name))
			GROUP BY product_name
			ORDER BY units_sold DESC, sales DESC
			LIMIT 10
			""", businessId, start(from), endExclusive(to), businessId);
	}

	private Map<String, Object> range(LocalDate from, LocalDate to) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("from", from.toString());
		result.put("to", to.toString());
		return result;
	}

	private LocalDateTime start(LocalDate date) {
		return date.atStartOfDay();
	}

	private LocalDateTime endExclusive(LocalDate date) {
		return date.plusDays(1).atStartOfDay();
	}
}
