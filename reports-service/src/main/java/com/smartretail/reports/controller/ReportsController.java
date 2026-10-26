package com.smartretail.reports.controller;

import com.smartretail.reports.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.List;
import java.io.IOException;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
	private final ReportService reports;
	private final com.smartretail.reports.service.ReportExportService exports;

	public ReportsController(ReportService reports, com.smartretail.reports.service.ReportExportService exports) {
		this.reports = reports;
		this.exports = exports;
	}

	@GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> exportPdf(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			HttpServletRequest request) throws IOException {
		DateRange range = range(from, to);
		byte[] pdf = exports.createPdf(businessId(request), range.from(), range.to());
		String filename = "smart-retail-report-" + range.from() + "-to-" + range.to() + ".pdf";
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
			.body(pdf);
	}

	@GetMapping("/overview")
	public Map<String, Object> overview(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			HttpServletRequest request) {
		DateRange range = range(from, to);
		return reports.overview(businessId(request), range.from(), range.to());
	}

	@GetMapping("/sales")
	public Map<String, Object> sales(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			HttpServletRequest request) {
		DateRange range = range(from, to);
		return reports.sales(businessId(request), range.from(), range.to());
	}

	@GetMapping("/inventory")
	public Map<String, Object> inventory(HttpServletRequest request) {
		return reports.inventory(businessId(request));
	}

	@GetMapping("/tax")
	public Map<String, Object> tax(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			HttpServletRequest request) {
		DateRange range = range(from, to);
		return reports.tax(businessId(request), range.from(), range.to());
	}

	@GetMapping("/customers")
	public Map<String, Object> customers(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			HttpServletRequest request) {
		DateRange range = range(from, to);
		return reports.customers(businessId(request), range.from(), range.to());
	}

	@GetMapping("/invoices")
	public Map<String, Object> invoices(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "false") boolean all,
			HttpServletRequest request) {
		if (page < 0 || size < 1 || size > 100) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be zero or greater and page size must be between 1 and 100.");
		}
		if (search != null && search.length() > 120) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text must be 120 characters or fewer.");
		}
		DateRange range = range(from, to);
		return reports.invoices(businessId(request), range.from(), range.to(), page, size, search, all);
	}

	@GetMapping("/invoices/{invoiceId}/items")
	public List<Map<String, Object>> invoiceItems(
			@PathVariable Long invoiceId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "false") boolean all,
			HttpServletRequest request) {
		DateRange range = range(from, to);
		return reports.invoiceItems(businessId(request), invoiceId, range.from(), range.to(), all);
	}

	@GetMapping("/products/combinations")
	public Map<String, Object> productCombinations(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			HttpServletRequest request) {
		DateRange range = range(from, to);
		return reports.productCombinations(businessId(request), range.from(), range.to());
	}

	private DateRange range(LocalDate from, LocalDate to) {
		LocalDate end = to == null ? LocalDate.now(BUSINESS_ZONE) : to;
		LocalDate start = from == null ? end.minusDays(29) : from;
		if (start.isAfter(end)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be on or before end date.");
		}
		return new DateRange(start, end);
	}

	private Long businessId(HttpServletRequest request) {
		return (Long) request.getAttribute("businessId");
	}

	private record DateRange(LocalDate from, LocalDate to) { }
}
