package com.smartretail.billing.controller;
import com.smartretail.billing.dto.SaleRequest;
import com.smartretail.billing.entity.BusinessProfile;
import com.smartretail.billing.entity.Invoice;
import com.smartretail.billing.entity.SaleItem;
import com.smartretail.billing.repository.BusinessProfileRepository;
import com.smartretail.billing.repository.InvoiceRepository;
import com.smartretail.billing.service.InventoryStockClient;
import com.smartretail.billing.service.InvoiceEmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
	private static final Logger log = LoggerFactory.getLogger(BillingController.class);
	private static final java.util.regex.Pattern GSTIN_PATTERN = java.util.regex.Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
	private final InvoiceRepository invoices;
	private final BusinessProfileRepository businesses;
	private final InventoryStockClient inventoryStockClient;
	private final InvoiceEmailService invoiceEmailService;
	private final JdbcTemplate jdbcTemplate;

	public BillingController(
			InvoiceRepository invoices,
			BusinessProfileRepository businesses,
			InventoryStockClient inventoryStockClient,
			InvoiceEmailService invoiceEmailService,
			JdbcTemplate jdbcTemplate) {
		this.invoices = invoices;
		this.businesses = businesses;
		this.inventoryStockClient = inventoryStockClient;
		this.invoiceEmailService = invoiceEmailService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostMapping("/sales")
	@Transactional
	public ResponseEntity<Map<String, Object>> sell(
			@Valid @RequestBody SaleRequest request,
			HttpServletRequest servlet) {
		Long businessId = business(servlet);
		String paymentMethod = request.paymentMethod() == null || request.paymentMethod().isBlank()
				? "Cash" : request.paymentMethod().trim();
		if ("Balance".equals(paymentMethod) && request.customerId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Select a saved customer before recording a balance bill.");
		}
		if ("Balance".equals(paymentMethod)
				&& (request.paymentDueAt() == null || !request.paymentDueAt().toInstant().isAfter(Instant.now()))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Choose a future payment reminder date and time.");
		}
		CustomerBillingDetails customerDetails = null;
		if (request.customerId() != null) {
			List<CustomerBillingDetails> matches = jdbcTemplate.query(
					"SELECT name, phone, email, address FROM customers WHERE id = ? AND business_id = ?",
					(resultSet, rowNum) -> new CustomerBillingDetails(
							resultSet.getString("name"), resultSet.getString("phone"),
							resultSet.getString("email"), resultSet.getString("address")),
					request.customerId(), businessId);
			if (matches.isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"The selected customer could not be found for this business. Select the customer again.");
			}
			customerDetails = matches.get(0);
		}
		if ("Balance".equals(paymentMethod)
				&& (customerDetails.email() == null || customerDetails.email().isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Add an email address to the selected customer before scheduling a payment reminder.");
		}
		BusinessProfile businessProfile = businesses.findById(businessId)
				.orElseThrow(() -> new ResponseStatusException(
						HttpStatus.NOT_FOUND,
						"Business profile was not found."));
		String sellerGstin = businessProfile.getGstin();
		if (sellerGstin != null && sellerGstin.isBlank()) {
			sellerGstin = null;
		}
		if (sellerGstin != null) sellerGstin = sellerGstin.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
		if (request.gstInvoice() && sellerGstin == null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Save your business GSTIN in Profile before creating a GST invoice.");
		}
		if (request.gstInvoice() && !GSTIN_PATTERN.matcher(sellerGstin).matches()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"The GSTIN saved in your business profile is invalid. Update it before creating a GST invoice.");
		}

		BigDecimal subtotal = request.items().stream()
				.map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal discount = request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount();
		if (discount.compareTo(subtotal) > 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Discount cannot exceed the bill subtotal.");
		}

		BigDecimal fallbackTaxRate = request.gstRate() == null ? BigDecimal.ZERO : request.gstRate();
		if (request.gstInvoice() && !isSupportedGstRate(fallbackTaxRate)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Choose a supported GST rate: 0%, 5%, 12%, 18%, or 28%.");
		}

		BigDecimal amountDue = subtotal.subtract(discount);
		BigDecimal taxableAmount = amountDue;
		BigDecimal cgst = BigDecimal.ZERO;
		BigDecimal sgst = BigDecimal.ZERO;
		BigDecimal igst = BigDecimal.ZERO;
		BigDecimal gstAmount = BigDecimal.ZERO;
		Map<BigDecimal, BigDecimal> taxableByRate = new java.util.TreeMap<>();
		Map<BigDecimal, BigDecimal> taxByRate = new java.util.TreeMap<>();
		List<BigDecimal> lineGstRates = new ArrayList<>();
		List<BigDecimal> lineGstAmounts = new ArrayList<>();
		List<BigDecimal> lineTaxableAmounts = new ArrayList<>();
		List<BigDecimal> lineDiscountAmounts = new ArrayList<>();
		if (request.gstInvoice()) {
			BigDecimal allocatedDiscountTotal = BigDecimal.ZERO;
			for (int index = 0; index < request.items().size(); index++) {
				SaleRequest.Line item = request.items().get(index);
				BigDecimal itemRate = item.gstRate() == null ? fallbackTaxRate : item.gstRate();
				if (!isSupportedGstRate(itemRate)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Choose a supported GST rate: 0%, 5%, 12%, 18%, or 28%.");
				}
				BigDecimal itemAmount = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
				BigDecimal allocatedDiscount;
				if (index == request.items().size() - 1) {
					allocatedDiscount = discount.subtract(allocatedDiscountTotal);
				} else {
					allocatedDiscount = subtotal.signum() == 0 ? BigDecimal.ZERO
							: discount.multiply(itemAmount).divide(subtotal, 2, RoundingMode.HALF_UP);
					allocatedDiscountTotal = allocatedDiscountTotal.add(allocatedDiscount);
				}
				BigDecimal lineGross = itemAmount.subtract(allocatedDiscount).max(BigDecimal.ZERO);
				BigDecimal lineTax = itemRate.signum() == 0 ? BigDecimal.ZERO
						: lineGross.multiply(itemRate).divide(BigDecimal.valueOf(100).add(itemRate), 2, RoundingMode.HALF_UP);
				BigDecimal lineTaxable = lineGross.subtract(lineTax);
				taxableByRate.merge(itemRate, lineTaxable, BigDecimal::add);
				taxByRate.merge(itemRate, lineTax, BigDecimal::add);
				lineGstRates.add(itemRate);
				lineGstAmounts.add(lineTax);
				lineTaxableAmounts.add(lineTaxable);
				lineDiscountAmounts.add(allocatedDiscount);
			}
			for (Map.Entry<BigDecimal, BigDecimal> entry : taxByRate.entrySet()) {
				BigDecimal rateTax = entry.getValue();
				gstAmount = gstAmount.add(rateTax);
				if (request.intraState()) {
					BigDecimal half = rateTax.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
					cgst = cgst.add(half);
					sgst = sgst.add(rateTax.subtract(half));
				}
			}
			if (!request.intraState()) {
				igst = gstAmount;
			}
			taxableAmount = amountDue.subtract(gstAmount);
		}
		BigDecimal totalAmount = amountDue;
		BigDecimal paidAmount = "Balance".equals(paymentMethod) ? BigDecimal.ZERO : totalAmount;
		BigDecimal balanceDue = totalAmount.subtract(paidAmount).max(BigDecimal.ZERO);
		String paymentStatus = balanceDue.signum() > 0 ? "UNPAID" : "PAID";
		BigDecimal cashPaid = BigDecimal.ZERO;
		BigDecimal upiPaid = BigDecimal.ZERO;
		BigDecimal cardPaid = BigDecimal.ZERO;
		BigDecimal creditCardPaid = BigDecimal.ZERO;
		BigDecimal debitCardPaid = BigDecimal.ZERO;
		if ("Split".equals(paymentMethod)) {
			SaleRequest.SplitPayment split = request.splitPayment();
			if (split == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the amount for each split payment method.");
			}
			cashPaid = zeroIfNull(split.cash());
			upiPaid = zeroIfNull(split.upi());
			creditCardPaid = zeroIfNull(split.creditCard());
			debitCardPaid = zeroIfNull(split.debitCard());
			BigDecimal splitTotal = cashPaid.add(upiPaid).add(creditCardPaid).add(debitCardPaid);
			if (splitTotal.compareTo(totalAmount) != 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Split payment amounts must equal the bill total.");
			}
		} else {
			switch (paymentMethod) {
				case "Cash" -> cashPaid = paidAmount;
				case "UPI", "Online" -> upiPaid = paidAmount;
				case "Card" -> cardPaid = paidAmount;
				case "Credit Card" -> creditCardPaid = paidAmount;
				case "Debit Card" -> debitCardPaid = paidAmount;
				default -> { }
			}
		}

		List<SaleItem> lines = new ArrayList<>();
		BigDecimal cost = BigDecimal.ZERO;
		BigDecimal productSavings = BigDecimal.ZERO;
		for (int index = 0; index < request.items().size(); index++) {
			SaleRequest.Line item = request.items().get(index);
			SaleItem line = new SaleItem();
			line.setProductName(item.productName().trim());
			line.setQuantity(item.quantity());
			line.setUnitPrice(item.unitPrice());
			line.setUnitCost(item.unitCost());
			BigDecimal mrpPrice = item.mrpPrice() == null ? item.unitPrice() : item.mrpPrice();
			BigDecimal lineSavings = mrpPrice.subtract(item.unitPrice()).max(BigDecimal.ZERO)
					.multiply(BigDecimal.valueOf(item.quantity()));
			line.setMrpPrice(mrpPrice);
			line.setProductDiscount(lineSavings);
			BigDecimal lineGstRate = request.gstInvoice() ? lineGstRates.get(index) : BigDecimal.ZERO;
			line.setGstRate(lineGstRate);
			line.setGstAmount(request.gstInvoice() ? lineGstAmounts.get(index) : BigDecimal.ZERO);
			line.setBillDiscount(request.gstInvoice() ? lineDiscountAmounts.get(index) : BigDecimal.ZERO);
			line.setTaxableAmount(request.gstInvoice()
					? lineTaxableAmounts.get(index)
					: item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
			lines.add(line);
			productSavings = productSavings.add(lineSavings);
			cost = cost.add(item.unitCost().multiply(BigDecimal.valueOf(item.quantity())));
		}

		Invoice invoice = new Invoice();
		invoice.setBusinessId(businessId);
		invoice.setSellerGstin(request.gstInvoice() ? sellerGstin : null);
		invoice.setGstInvoice(request.gstInvoice());
		invoice.setDiscountAmount(discount);
		invoice.setTaxableAmount(taxableAmount);
		BigDecimal invoiceRate = taxableByRate.size() == 1
				? taxableByRate.keySet().iterator().next() : BigDecimal.ZERO;
		invoice.setGstRate(request.gstInvoice() ? invoiceRate : BigDecimal.ZERO);
		invoice.setCgstAmount(cgst);
		invoice.setSgstAmount(sgst);
		invoice.setIgstAmount(igst);
		invoice.setTotalAmount(totalAmount);
		invoice.setPaymentMethod(paymentMethod);
		invoice.setPaymentStatus(paymentStatus);
		invoice.setPaidAmount(paidAmount);
		invoice.setBalanceDue(balanceDue);
		invoice.setCashPaymentAmount(cashPaid);
		invoice.setUpiPaymentAmount(upiPaid);
		invoice.setCardPaymentAmount(cardPaid);
		invoice.setCreditCardPaymentAmount(creditCardPaid);
		invoice.setDebitCardPaymentAmount(debitCardPaid);
		invoice.setPaymentDueAt("Balance".equals(paymentMethod) ? request.paymentDueAt().toInstant() : null);
		invoice.setBuyerName(customerDetails != null ? customerDetails.name()
				: request.buyerName() == null ? null : request.buyerName().trim());
		invoice.setCustomerId(request.customerId());
		invoice.setBuyerGstin(request.buyerGstin() == null ? null : request.buyerGstin().trim().toUpperCase());
		invoice.setInvoiceNumber("INV-" + System.currentTimeMillis());
		invoice.setSoldAt(LocalDateTime.now(BUSINESS_ZONE));

		invoice.setItems(lines);
		invoice.setRevenue(taxableAmount);
		invoice.setCost(cost);
		Invoice saved = invoices.save(invoice);
		String authorization = servlet.getHeader("Authorization");
		inventoryStockClient.deductStock(authorization, request.items());
		String customerEmail = customerDetails == null ? null : customerDetails.email();
		String emailMessage = customerDetails == null
				? "No email was sent. Select a saved customer with an email address to receive this invoice."
				: "No email was sent because this customer has no saved email address.";
		boolean emailSent = false;
		if (customerEmail != null && !customerEmail.isBlank()) {
			try {
				invoiceEmailService.sendInvoice(customerEmail.trim(), businessProfile, saved,
						customerDetails.phone(), customerDetails.address());
				emailSent = true;
				emailMessage = "Invoice email sent to " + customerEmail.trim() + ".";
			} catch (Exception emailError) {
				log.warn("Invoice email delivery failed for invoice {}: {}: {}", saved.getInvoiceNumber(),
						emailError.getClass().getSimpleName(), emailError.getMessage());
				emailMessage = "The bill was saved, but email delivery failed. Check SMTP settings and retry.";
			}
		} else if (customerDetails != null) {
			emailMessage = "No email was sent. Add an email address to this customer's profile, then resend the invoice.";
		}
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("id", saved.getId());
		response.put("customerId", saved.getCustomerId());
		response.put("invoiceNumber", saved.getInvoiceNumber());
		response.put("soldAt", saved.getSoldAt());
		response.put("sellerGstin", saved.getSellerGstin() == null ? "" : saved.getSellerGstin());
		response.put("gstInvoice", saved.isGstInvoice());
		response.put("subtotal", subtotal);
		response.put("productSavings", productSavings);
		response.put("totalSavings", productSavings.add(saved.getDiscountAmount()));
		response.put("discountAmount", saved.getDiscountAmount());
		response.put("taxableAmount", saved.getTaxableAmount());
		response.put("gstRate", saved.getGstRate());
		response.put("gstBreakdown", taxableByRate.entrySet().stream().map(entry -> {
			Map<String, Object> rateBreakdown = new LinkedHashMap<>();
			rateBreakdown.put("rate", entry.getKey());
			rateBreakdown.put("taxableAmount", entry.getValue());
			rateBreakdown.put("gstAmount", taxByRate.getOrDefault(entry.getKey(), BigDecimal.ZERO));
			return rateBreakdown;
		}).toList());
		response.put("cgst", saved.getCgstAmount());
		response.put("sgst", saved.getSgstAmount());
		response.put("igst", saved.getIgstAmount());
		response.put("total", saved.getTotalAmount());
		response.put("paymentMethod", saved.getPaymentMethod());
		response.put("paymentStatus", saved.getPaymentStatus());
		response.put("paidAmount", saved.getPaidAmount());
		response.put("balanceDue", saved.getBalanceDue());
		response.put("paymentBreakdown", paymentBreakdown(saved));
		response.put("paymentDueAt", saved.getPaymentDueAt());
		response.put("revenue", saved.getRevenue());
		response.put("cost", saved.getCost());
		response.put("profit", saved.getRevenue().subtract(saved.getCost()));
		response.put("customerEmail", customerEmail == null ? "" : customerEmail);
		response.put("customerPhone", customerDetails == null ? "" : customerDetails.phone());
		response.put("customerAddress", customerDetails == null ? "" : customerDetails.address());
		response.put("emailSent", emailSent);
		response.put("emailMessage", emailMessage);
		return ResponseEntity.status(201).body(response);
	}

	@PostMapping("/invoices/{invoiceId}/email")
	public ResponseEntity<Map<String, Object>> resendInvoiceEmail(
			@PathVariable Long invoiceId,
			HttpServletRequest servlet) {
		Long businessId = business(servlet);
		Invoice invoice = invoices.findById(invoiceId)
				.filter(saved -> saved.getBusinessId().equals(businessId))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found."));
		if (invoice.getCustomerId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"This bill has no saved customer. Attach a customer with an email address to send it.");
		}
		List<CustomerBillingDetails> matches = jdbcTemplate.query(
				"SELECT name, phone, email, address FROM customers WHERE id = ? AND business_id = ?",
				(resultSet, rowNum) -> new CustomerBillingDetails(
						resultSet.getString("name"), resultSet.getString("phone"),
						resultSet.getString("email"), resultSet.getString("address")),
				invoice.getCustomerId(), businessId);
		if (matches.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"The saved customer could not be found. Update the customer record and try again.");
		}
		CustomerBillingDetails customer = matches.get(0);
		if (customer.email() == null || customer.email().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"This customer has no saved email address. Add one to the customer profile and try again.");
		}
		BusinessProfile businessProfile = businesses.findById(businessId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business profile not found."));
		try {
			invoiceEmailService.sendInvoice(customer.email().trim(), businessProfile, invoice,
					customer.phone(), customer.address());
			log.info("Invoice email sent for invoice {}", invoice.getInvoiceNumber());
			return ResponseEntity.ok(Map.of(
					"emailSent", true,
					"customerEmail", customer.email(),
					"emailMessage", "Invoice email sent to " + customer.email().trim() + "."));
		} catch (Exception emailError) {
			log.warn("Invoice email retry failed for invoice {}: {}: {}", invoice.getInvoiceNumber(),
					emailError.getClass().getSimpleName(), emailError.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
					"emailSent", false,
					"customerEmail", customer.email(),
					"emailMessage", "Email could not be sent. Check the Gmail App Password and restart the billing service."));
		}
	}

	private record CustomerBillingDetails(String name, String phone, String email, String address) {}

	private boolean isSupportedGstRate(BigDecimal rate) {
		return Set.of("0", "5", "12", "18", "28").stream()
				.map(BigDecimal::new)
				.anyMatch(supported -> supported.compareTo(rate) == 0);
	}

	@GetMapping("/summary/today")
	public Map<String, Object> today(HttpServletRequest servlet) {
		LocalDateTime start = LocalDate.now(BUSINESS_ZONE).atStartOfDay();
		LocalDateTime end = start.plusDays(1);
		List<Invoice> list = invoices.findAllByBusinessIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtDesc(
				business(servlet), start, end);
		BigDecimal revenue = sumRevenue(list);
		BigDecimal sales = sumSales(list);
		BigDecimal cost = sumCost(list);
		return Map.of(
				"saleCount", list.size(),
				"sales", sales,
				"revenue", revenue,
				"cost", cost,
				"profit", revenue.subtract(cost));
	}

	@GetMapping("/dashboard")
	public Map<String, Object> dashboard(
			@RequestParam(required = false) Integer requestedDays,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			HttpServletRequest servlet) {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		if (from == null && to == null) {
			int days = requestedDays != null && requestedDays == 30 ? 30 : 7;
			to = today;
			from = today.minusDays(days - 1L);
		} else if (from == null || to == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both start and end dates are required.");
		}
		if (from.isAfter(to)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be on or before end date.");
		}
		long days = ChronoUnit.DAYS.between(from, to) + 1;
		if (days > 366) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a date range of 366 days or less.");
		}
		LocalDateTime start = from.atStartOfDay();
		LocalDateTime end = to.plusDays(1).atStartOfDay();
		LocalDateTime previousStart = start.minusDays(days);
		Long businessId = business(servlet);

		List<Invoice> current = invoices.findAllByBusinessIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtDesc(
				businessId, start, end);
		List<Invoice> previous = invoices.findAllByBusinessIdAndSoldAtGreaterThanEqualAndSoldAtLessThanOrderBySoldAtDesc(
			businessId, previousStart, start);
		BigDecimal currentSales = sumSales(current);
		BigDecimal previousSales = sumSales(previous);
		BigDecimal currentRevenue = sumRevenue(current);
		Map<String, BigDecimal> collectedByMethod = new LinkedHashMap<>();
		Map<String, Integer> paidBillCountByMethod = new LinkedHashMap<>();
		BigDecimal outstandingAmount = BigDecimal.ZERO;
		int outstandingBillCount = 0;
		for (Invoice invoice : current) {
			BigDecimal paid = paymentCollected(invoice);
			for (Map.Entry<String, BigDecimal> tender : paymentBreakdown(invoice).entrySet()) {
				if (tender.getValue().signum() > 0) {
					collectedByMethod.merge(tender.getKey(), tender.getValue(), BigDecimal::add);
					paidBillCountByMethod.merge(tender.getKey(), 1, Integer::sum);
				}
			}
			BigDecimal due = paymentOutstanding(invoice);
			outstandingAmount = outstandingAmount.add(due);
			if (due.signum() > 0) outstandingBillCount++;
		}
		List<Map<String, Object>> paymentMethods = collectedByMethod.entrySet().stream()
				.map(entry -> Map.<String, Object>of(
						"method", entry.getKey(),
						"amount", entry.getValue(),
						"billCount", paidBillCountByMethod.getOrDefault(entry.getKey(), 0)))
				.toList();
		Map<LocalDate, List<Invoice>> invoicesByDate = current.stream().collect(
				Collectors.groupingBy(invoice -> invoice.getSoldAt().toLocalDate()));

		List<Map<String, Object>> dailySales = new ArrayList<>();
		if (days == 1) {
			for (int hour = 0; hour < 24; hour++) {
				final int bucketHour = hour;
				List<Invoice> hourInvoices = current.stream()
						.filter(invoice -> invoice.getSoldAt().getHour() == bucketHour).toList();
				BigDecimal sales = sumSales(hourInvoices);
				BigDecimal revenue = sumRevenue(hourInvoices);
				BigDecimal cost = sumCost(hourInvoices);
				dailySales.add(Map.of(
						"date", from + "T" + String.format("%02d", hour),
						"label", java.time.LocalTime.of(hour, 0).format(java.time.format.DateTimeFormatter.ofPattern("h a", Locale.ENGLISH)),
						"sales", sales,
						"revenue", revenue,
						"profit", revenue.subtract(cost),
						"saleCount", hourInvoices.size()));
			}
		} else for (int offset = 0; offset < days; offset++) {
			LocalDate date = from.plusDays(offset);
			List<Invoice> dayInvoices = invoicesByDate.getOrDefault(date, List.of());
			BigDecimal revenue = sumRevenue(dayInvoices);
			BigDecimal sales = sumSales(dayInvoices);
			BigDecimal cost = sumCost(dayInvoices);
			dailySales.add(Map.of(
					"date", date.toString(),
					"sales", sales,
					"revenue", revenue,
					"profit", revenue.subtract(cost),
					"saleCount", dayInvoices.size()));
		}

		Map<String, ProductTotals> productTotals = new HashMap<>();
		for (Invoice invoice : current) {
			List<SaleItem> items = invoice.getItems();
			BigDecimal invoiceGross = items.stream()
					.map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			BigDecimal allocatedDiscount = BigDecimal.ZERO;
			for (int index = 0; index < items.size(); index++) {
				SaleItem item = items.get(index);
				BigDecimal gross = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
				BigDecimal lineSales;
				if (invoice.isGstInvoice()) {
					lineSales = item.getTaxableAmount().add(item.getGstAmount());
				} else {
					BigDecimal discountShare = BigDecimal.ZERO;
					if (invoice.getDiscountAmount().signum() > 0 && invoiceGross.signum() > 0) {
						discountShare = index == items.size() - 1
								? invoice.getDiscountAmount().subtract(allocatedDiscount)
								: invoice.getDiscountAmount().multiply(gross).divide(invoiceGross, 2, RoundingMode.HALF_UP);
						allocatedDiscount = allocatedDiscount.add(discountShare);
					}
					lineSales = gross.subtract(discountShare).max(BigDecimal.ZERO);
				}
				productTotals.computeIfAbsent(item.getProductName(), ignored -> new ProductTotals()).add(item, lineSales);
			}
		}
		List<Map<String, Object>> topProducts = productTotals.entrySet().stream()
				.sorted(Map.Entry.<String, ProductTotals>comparingByValue(
						Comparator.comparingInt(ProductTotals::getUnits).reversed()
								.thenComparing(ProductTotals::getRevenue, Comparator.reverseOrder())))
				.limit(5)
				.map(entry -> Map.<String, Object>of(
						"name", entry.getKey(),
						"units", entry.getValue().getUnits(),
						"sales", entry.getValue().getRevenue()))
				.toList();

		List<Map<String, Object>> recentSales = invoices.findTop6ByBusinessIdOrderBySoldAtDesc(businessId).stream()
				.map(invoice -> {
					Map<String, Object> sale = new LinkedHashMap<>();
					sale.put("invoiceNumber", invoice.getInvoiceNumber());
					sale.put("soldAt", formatSoldAt(invoice));
					sale.put("sellerGstin", invoice.getSellerGstin() == null ? "" : invoice.getSellerGstin());
					sale.put("total", invoice.getTotalAmount());
					sale.put("paymentStatus", paymentStatus(invoice));
					sale.put("paymentMethod", invoice.getPaymentMethod());
					sale.put("paidAmount", paymentCollected(invoice));
					sale.put("balanceDue", paymentOutstanding(invoice));
					sale.put("paymentBreakdown", paymentBreakdown(invoice));
					sale.put("revenue", invoice.getRevenue());
					sale.put("itemCount", invoice.getItems().size());
					return sale;
				})
				.toList();

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("days", days);
		response.put("from", from.toString());
		response.put("to", to.toString());
		response.put("currentSales", currentSales);
		response.put("currentRevenue", currentRevenue);
		response.put("previousSales", previousSales);
		response.put("salesChangePercent", previousSales.signum() == 0
				? null
				: currentSales.subtract(previousSales)
						.multiply(BigDecimal.valueOf(100))
						.divide(previousSales, 1, RoundingMode.HALF_UP));
		response.put("revenueChangePercent", response.get("salesChangePercent"));
		response.put("saleCount", current.size());
		response.put("profit", currentRevenue.subtract(sumCost(current)));
		response.put("paymentSummary", Map.of(
				"collectedAmount", collectedByMethod.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
				"methods", paymentMethods,
				"outstandingAmount", outstandingAmount,
				"outstandingBillCount", outstandingBillCount));
		response.put("dailySales", dailySales);
		response.put("topProducts", topProducts);
		response.put("recentSales", recentSales);
		return response;
	}

	@GetMapping("/invoices")
	public List<Map<String, Object>> invoices(
			@RequestParam(required = false) String customerName,
			@RequestParam(required = false) Long customerId,
			HttpServletRequest servlet) {
		Long businessId = business(servlet);
		List<Invoice> list;
		if (customerId != null) {
			list = new ArrayList<>(invoices.findAllByBusinessIdAndCustomerIdOrderBySoldAtDesc(businessId, customerId));
			if (customerName != null && !customerName.trim().isEmpty()) {
				list.addAll(invoices.findAllByBusinessIdAndCustomerIdIsNullAndBuyerNameIgnoreCaseOrderBySoldAtDesc(
						businessId, customerName.trim()));
				list.sort(Comparator.comparing(Invoice::getSoldAt).reversed());
			}
		} else if (customerName != null && !customerName.trim().isEmpty()) {
			list = invoices.findAllByBusinessIdAndBuyerNameContainingIgnoreCaseOrderBySoldAtDesc(
					businessId, customerName.trim());
		} else {
			list = invoices.findAllByBusinessIdOrderBySoldAtDesc(businessId);
		}

		return list.stream().map(invoice -> {
			Map<String, Object> invMap = new LinkedHashMap<>();
			invMap.put("id", invoice.getId());
			invMap.put("invoiceNumber", invoice.getInvoiceNumber());
			invMap.put("soldAt", formatSoldAt(invoice));
			invMap.put("buyerName", invoice.getBuyerName() == null ? "" : invoice.getBuyerName());
			invMap.put("customerId", invoice.getCustomerId());
			invMap.put("buyerGstin", invoice.getBuyerGstin() == null ? "" : invoice.getBuyerGstin());
			invMap.put("sellerGstin", invoice.getSellerGstin() == null ? "" : invoice.getSellerGstin());
			invMap.put("gstInvoice", invoice.isGstInvoice());
			invMap.put("discountAmount", invoice.getDiscountAmount());
			invMap.put("taxableAmount", invoice.getTaxableAmount());
			invMap.put("cgst", invoice.getCgstAmount());
			invMap.put("sgst", invoice.getSgstAmount());
			invMap.put("igst", invoice.getIgstAmount());
			invMap.put("total", invoice.getTotalAmount());
			invMap.put("paymentMethod", invoice.getPaymentMethod());
			invMap.put("paymentStatus", paymentStatus(invoice));
			invMap.put("paidAmount", paymentCollected(invoice));
			invMap.put("balanceDue", paymentOutstanding(invoice));
			invMap.put("paymentBreakdown", paymentBreakdown(invoice));
			invMap.put("paymentDueAt", invoice.getPaymentDueAt());
			invMap.put("revenue", invoice.getRevenue());
			invMap.put("cost", invoice.getCost());
			invMap.put("profit", invoice.getRevenue().subtract(invoice.getCost()));

			List<Map<String, Object>> itemMaps = invoice.getItems().stream().map(item -> {
				Map<String, Object> itemMap = new LinkedHashMap<>();
				itemMap.put("id", item.getId());
				itemMap.put("productName", item.getProductName());
				itemMap.put("quantity", item.getQuantity());
				itemMap.put("unitPrice", item.getUnitPrice());
				itemMap.put("unitCost", item.getUnitCost());
				itemMap.put("mrpPrice", item.getMrpPrice());
				itemMap.put("productDiscount", item.getProductDiscount());
				itemMap.put("gstRate", item.getGstRate());
				itemMap.put("gstAmount", item.getGstAmount());
				itemMap.put("billDiscount", item.getBillDiscount());
				itemMap.put("taxableAmount", item.getTaxableAmount());
				itemMap.put("lineTotal", item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
				return itemMap;
			}).toList();

			invMap.put("items", itemMaps);
			return invMap;
		}).toList();
	}

	private BigDecimal sumRevenue(List<Invoice> list) {
		return list.stream().map(Invoice::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal paymentCollected(Invoice invoice) {
		BigDecimal total = invoice.getTotalAmount().max(BigDecimal.ZERO);
		if ("Balance".equalsIgnoreCase(invoice.getPaymentMethod())) return BigDecimal.ZERO;
		return invoice.getPaidAmount().max(BigDecimal.ZERO).min(total);
	}

	private BigDecimal paymentOutstanding(Invoice invoice) {
		BigDecimal total = invoice.getTotalAmount().max(BigDecimal.ZERO);
		if ("Balance".equalsIgnoreCase(invoice.getPaymentMethod())) return total;
		BigDecimal storedDue = invoice.getBalanceDue().max(BigDecimal.ZERO);
		if (storedDue.signum() > 0) return storedDue.min(total);
		return total.subtract(paymentCollected(invoice)).max(BigDecimal.ZERO);
	}

	private String paymentStatus(Invoice invoice) {
		return paymentOutstanding(invoice).signum() > 0 ? "UNPAID" : "PAID";
	}

	private Map<String, BigDecimal> paymentBreakdown(Invoice invoice) {
		Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
		breakdown.put("Cash", invoice.getCashPaymentAmount());
		breakdown.put("UPI", invoice.getUpiPaymentAmount());
		breakdown.put("Card", invoice.getCardPaymentAmount());
		breakdown.put("Credit Card", invoice.getCreditCardPaymentAmount());
		breakdown.put("Debit Card", invoice.getDebitCardPaymentAmount());
		BigDecimal savedTenderTotal = breakdown.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		if (savedTenderTotal.signum() > 0 || paymentCollected(invoice).signum() == 0) return breakdown;
		String method = invoice.getPaymentMethod();
		if ("Online".equalsIgnoreCase(method)) method = "UPI";
		if (!breakdown.containsKey(method)) breakdown.put(method, BigDecimal.ZERO);
		breakdown.put(method, paymentCollected(invoice));
		return breakdown;
	}

	private static BigDecimal zeroIfNull(BigDecimal amount) {
		return amount == null ? BigDecimal.ZERO : amount;
	}

	private BigDecimal sumSales(List<Invoice> list) {
		return list.stream().map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal sumCost(List<Invoice> list) {
		return list.stream().map(Invoice::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private Long business(HttpServletRequest request) {
		return (Long) request.getAttribute("businessId");
	}

	private String formatSoldAt(Invoice invoice) {
		return invoice.getSoldAt().atZone(BUSINESS_ZONE).toOffsetDateTime().toString();
	}

	private static class ProductTotals {
		private int units;
		private BigDecimal revenue = BigDecimal.ZERO;

		private void add(SaleItem item, BigDecimal sales) {
			units += item.getQuantity();
			revenue = revenue.add(sales);
		}

		private int getUnits() {
			return units;
		}

		private BigDecimal getRevenue() {
			return revenue;
		}
	}
}
