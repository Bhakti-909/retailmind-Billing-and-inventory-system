package com.smartretail.reports.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportExportServiceTest {
	@Test
	void createsReadablePeriodScopedPdfUsingAuthorizedBusinessQueries() throws Exception {
		ReportService reports = mock(ReportService.class);
		Long businessId = 42L;
		LocalDate from = LocalDate.parse("2026-10-01");
		LocalDate to = LocalDate.parse("2026-10-11");
		when(reports.overview(businessId, from, to)).thenReturn(Map.of("summary", Map.of(
			"bill_count", 3, "total_sales", 1000, "net_revenue", 950, "cost_of_goods", 600,
			"gross_profit", 350, "average_bill", 333)));
		when(reports.inventory(businessId)).thenReturn(Map.of("summary", Map.of()));
		when(reports.tax(businessId, from, to)).thenReturn(Map.of("summary", Map.of()));
		when(reports.customers(businessId, from, to)).thenReturn(Map.of("summary", Map.of()));
		when(reports.productCombinations(businessId, from, to)).thenReturn(Map.of("combinations", List.of()));

		byte[] bytes = new ReportExportService(reports).createPdf(businessId, from, to);
		try (PDDocument pdf = PDDocument.load(new ByteArrayInputStream(bytes))) {
			String text = new PDFTextStripper().getText(pdf);
			assertTrue(pdf.getNumberOfPages() >= 1);
			assertTrue(text.contains("SMART RETAIL BUSINESS REPORT"));
			assertTrue(text.contains("Reporting period: 2026-10-01 through 2026-10-11"));
			assertTrue(text.contains("Recorded gross profit: INR 350"));
			assertTrue(text.contains("net profit is not calculated"));
		}
		verify(reports).overview(businessId, from, to);
		verify(reports).inventory(businessId);
		verify(reports).tax(businessId, from, to);
	}
}
