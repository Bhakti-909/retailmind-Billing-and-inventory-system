package com.smartretail.reports.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Produces a downloadable PDF from the same authorized report queries shown in the UI. */
@Service
public class ReportExportService {
	private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);
	private final ReportService reports;

	public ReportExportService(ReportService reports) { this.reports = reports; }

	public byte[] createPdf(Long businessId, LocalDate from, LocalDate to) throws IOException {
		long started = System.nanoTime();
		try {
			return createPdfContent(businessId, from, to);
		} finally {
			log.info("reports.stage name=pdf_generation duration_ms={}", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
		}
	}

	@SuppressWarnings("unchecked")
	private byte[] createPdfContent(Long businessId, LocalDate from, LocalDate to) throws IOException {
		Map<String, Object> overview = reports.overview(businessId, from, to);
		Map<String, Object> inventory = reports.inventory(businessId);
		Map<String, Object> tax = reports.tax(businessId, from, to);
		Map<String, Object> customers = reports.customers(businessId, from, to);
		Map<String, Object> combinations = reports.productCombinations(businessId, from, to);
		Map<String, Object> sales = (Map<String, Object>) overview.get("summary");
		Map<String, Object> stock = (Map<String, Object>) inventory.get("summary");
		Map<String, Object> taxSummary = (Map<String, Object>) tax.get("summary");
		Map<String, Object> customerSummary = (Map<String, Object>) customers.get("summary");
		List<String> lines = new ArrayList<>();
		lines.add("SMART RETAIL BUSINESS REPORT");
		lines.add("Reporting period: " + from + " through " + to + " (inclusive, Asia/Kolkata)");
		lines.add("");
		lines.add("SALES PERFORMANCE");
		lines.add("Bills: " + value(sales, "bill_count") + "   Total sales: INR " + value(sales, "total_sales"));
		lines.add("Net revenue: INR " + value(sales, "net_revenue") + "   Cost of goods: INR " + value(sales, "cost_of_goods"));
		lines.add("Recorded gross profit: INR " + value(sales, "gross_profit") + "   Average bill: INR " + value(sales, "average_bill"));
		lines.add("Gross profit excludes operating expenses; net profit is not calculated.");
		lines.add("");
		lines.add("DAILY SALES");
		for (Map<String, Object> row : rows(overview, "dailySales"))
			lines.add(value(row, "date") + " | " + value(row, "bill_count") + " bills | INR " + value(row, "total_sales") + " sales | INR " + value(row, "gross_profit") + " gross profit");
		lines.add("");
		lines.add("TOP PRODUCTS (BY UNITS SOLD)");
		for (Map<String, Object> row : rows(overview, "topProducts"))
			lines.add(value(row, "product_name") + " | " + value(row, "units_sold") + " units | INR " + value(row, "sales") + " sales | INR " + value(row, "gross_profit") + " gross profit");
		lines.add("");
		lines.add("INVENTORY POSITION");
		lines.add("Products: " + value(stock, "product_count") + " | Units on hand: " + value(stock, "units_on_hand") + " | Low stock: " + value(stock, "low_stock_count") + " | Out of stock: " + value(stock, "out_of_stock_count"));
		lines.add("Cost value: INR " + value(stock, "cost_value") + " | Retail value: INR " + value(stock, "retail_value") + " | Expired: " + value(stock, "expired_count") + " | Expiring soon: " + value(stock, "expiring_soon_count"));
		lines.add("Products requiring review:");
		for (Map<String, Object> row : rows(inventory, "attentionItems"))
			lines.add(value(row, "name") + " | SKU " + value(row, "sku") + " | on hand " + value(row, "quantity") + " | reorder at " + value(row, "reorder_level") + " | expiry " + value(row, "expiry_date"));
		lines.add("");
		lines.add("TAX SUMMARY");
		lines.add("Taxable sales: INR " + value(taxSummary, "taxable_sales") + " | CGST: INR " + value(taxSummary, "cgst") + " | SGST: INR " + value(taxSummary, "sgst") + " | IGST: INR " + value(taxSummary, "igst"));
		lines.add("Tax collected: INR " + value(taxSummary, "tax_collected"));
		lines.add("");
		lines.add("CUSTOMER ACTIVITY");
		lines.add("Customers: " + value(customerSummary, "customer_count") + " | Bills: " + value(customerSummary, "bill_count") + " | Sales: INR " + value(customerSummary, "customer_sales"));
		for (Map<String, Object> row : rows(customers, "topCustomers"))
			lines.add(value(row, "customer_name") + " | " + value(row, "bill_count") + " bills | INR " + value(row, "total_spend") + " spend | INR " + value(row, "gross_profit") + " gross profit");
		lines.add("");
		lines.add("FREQUENTLY BOUGHT TOGETHER");
		for (Map<String, Object> row : rows(combinations, "combinations"))
			lines.add(value(row, "product_a") + " + " + value(row, "product_b") + " | " + value(row, "bills_together") + " bills | INR " + value(row, "combined_sales") + " sales");
		return pdf(lines);
	}

	private byte[] pdf(List<String> lines) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			PDPageContentStream stream = new PDPageContentStream(document, page);
			float y = 790;
			for (int i = 0; i < lines.size(); i++) {
				if (y < 48) {
					stream.close();
					page = new PDPage(PDRectangle.A4);
					document.addPage(page);
					stream = new PDPageContentStream(document, page);
					y = 790;
				}
				String line = ascii(lines.get(i));
				stream.beginText();
				stream.setFont(i == 0 ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, i == 0 ? 16 : 9);
				stream.newLineAtOffset(42, y);
				stream.showText(line.length() > 125 ? line.substring(0, 122) + "..." : line);
				stream.endText();
				y -= i == 0 ? 28 : 15;
			}
			stream.close();
			document.save(output);
			return output.toByteArray();
		}
	}

	private String ascii(String value) { return value == null ? "-" : value.replaceAll("[^\\x20-\\x7E]", "?"); }
	private String value(Map<String, Object> map, String key) { return map == null || map.get(key) == null ? "-" : ascii(String.valueOf(map.get(key))); }
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> rows(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
	}
}
