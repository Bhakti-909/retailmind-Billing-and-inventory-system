package com.smartretail.billing.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "business_id", nullable = false)
	private Long businessId;

	@Column(name = "seller_gstin", length = 15)
	private String sellerGstin;

	@Column(name = "gst_invoice")
	private Boolean gstInvoice = false;

	@Column(name = "discount_amount", precision = 12, scale = 2)
	private BigDecimal discountAmount = BigDecimal.ZERO;

	@Column(name = "taxable_amount", precision = 12, scale = 2)
	private BigDecimal taxableAmount = BigDecimal.ZERO;

	@Column(name = "gst_rate", precision = 5, scale = 2)
	private BigDecimal gstRate = BigDecimal.ZERO;

	@Column(name = "cgst_amount", precision = 12, scale = 2)
	private BigDecimal cgstAmount = BigDecimal.ZERO;

	@Column(name = "sgst_amount", precision = 12, scale = 2)
	private BigDecimal sgstAmount = BigDecimal.ZERO;

	@Column(name = "igst_amount", precision = 12, scale = 2)
	private BigDecimal igstAmount = BigDecimal.ZERO;

	@Column(name = "total_amount", precision = 12, scale = 2)
	private BigDecimal totalAmount = BigDecimal.ZERO;

	@Column(name = "payment_method", length = 32)
	private String paymentMethod = "Cash";

	@Column(name = "payment_status", length = 16)
	private String paymentStatus = "PAID";

	@Column(name = "paid_amount", precision = 12, scale = 2)
	private BigDecimal paidAmount = BigDecimal.ZERO;

	@Column(name = "balance_due", precision = 12, scale = 2)
	private BigDecimal balanceDue = BigDecimal.ZERO;

	@Column(name = "payment_cash_amount", precision = 12, scale = 2)
	private BigDecimal cashPaymentAmount = BigDecimal.ZERO;

	@Column(name = "payment_upi_amount", precision = 12, scale = 2)
	private BigDecimal upiPaymentAmount = BigDecimal.ZERO;

	@Column(name = "payment_card_amount", precision = 12, scale = 2)
	private BigDecimal cardPaymentAmount = BigDecimal.ZERO;

	@Column(name = "payment_credit_card_amount", precision = 12, scale = 2)
	private BigDecimal creditCardPaymentAmount = BigDecimal.ZERO;

	@Column(name = "payment_debit_card_amount", precision = 12, scale = 2)
	private BigDecimal debitCardPaymentAmount = BigDecimal.ZERO;

	@Column(name = "payment_due_at")
	private Instant paymentDueAt;

	@Column(name = "payment_reminder_sent_at")
	private Instant paymentReminderSentAt;

	@Column(name = "buyer_name")
	private String buyerName;

	// Kept as a scalar because customer records belong to the customer service.
	// Nullable so existing invoices remain valid after the schema update.
	@Column(name = "customer_id")
	private Long customerId;

	@Column(name = "buyer_gstin", length = 15)
	private String buyerGstin;

	@Column(nullable = false)
	private String invoiceNumber;

	@Column(nullable = false)
	private LocalDateTime soldAt;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal revenue;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal cost;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "invoice_id")
	private List<SaleItem> items = new ArrayList<>();

	public Long getId() { return id; }
	public Long getBusinessId() { return businessId; }
	public void setBusinessId(Long value) { businessId = value; }
	public String getSellerGstin() { return sellerGstin; }
	public void setSellerGstin(String value) { sellerGstin = value; }
	public boolean isGstInvoice() { return Boolean.TRUE.equals(gstInvoice); }
	public void setGstInvoice(boolean value) { gstInvoice = value; }
	public BigDecimal getDiscountAmount() { return discountAmount == null ? BigDecimal.ZERO : discountAmount; }
	public void setDiscountAmount(BigDecimal value) { discountAmount = value; }
	public BigDecimal getTaxableAmount() { return taxableAmount == null ? revenue : taxableAmount; }
	public void setTaxableAmount(BigDecimal value) { taxableAmount = value; }
	public BigDecimal getGstRate() { return gstRate == null ? BigDecimal.ZERO : gstRate; }
	public void setGstRate(BigDecimal value) { gstRate = value; }
	public BigDecimal getCgstAmount() { return cgstAmount == null ? BigDecimal.ZERO : cgstAmount; }
	public void setCgstAmount(BigDecimal value) { cgstAmount = value; }
	public BigDecimal getSgstAmount() { return sgstAmount == null ? BigDecimal.ZERO : sgstAmount; }
	public void setSgstAmount(BigDecimal value) { sgstAmount = value; }
	public BigDecimal getIgstAmount() { return igstAmount == null ? BigDecimal.ZERO : igstAmount; }
	public void setIgstAmount(BigDecimal value) { igstAmount = value; }
	public BigDecimal getTotalAmount() { return totalAmount == null ? revenue : totalAmount; }
	public void setTotalAmount(BigDecimal value) { totalAmount = value; }
	public String getPaymentMethod() { return paymentMethod == null || paymentMethod.isBlank() ? "Cash" : paymentMethod; }
	public void setPaymentMethod(String value) { paymentMethod = value; }
	public String getPaymentStatus() {
		// Balance bills are credit sales; old rows may have been stored as paid.
		if ("Balance".equalsIgnoreCase(getPaymentMethod())) {
			return getTotalAmount().signum() > 0 ? "UNPAID" : "PAID";
		}
		if (paymentStatus != null && !paymentStatus.isBlank()) return paymentStatus;
		return getBalanceDue().signum() > 0 ? "UNPAID" : "PAID";
	}
	public void setPaymentStatus(String value) { paymentStatus = value; }
	public BigDecimal getPaidAmount() {
		if ("Balance".equalsIgnoreCase(getPaymentMethod())) return BigDecimal.ZERO;
		if (paidAmount != null) return paidAmount;
		return getTotalAmount();
	}
	public void setPaidAmount(BigDecimal value) { paidAmount = value; }
	public BigDecimal getBalanceDue() {
		if ("Balance".equalsIgnoreCase(getPaymentMethod())) return getTotalAmount().max(BigDecimal.ZERO);
		if (balanceDue != null) return balanceDue;
		return BigDecimal.ZERO;
	}
	public void setBalanceDue(BigDecimal value) { balanceDue = value; }
	public BigDecimal getCashPaymentAmount() { return cashPaymentAmount == null ? BigDecimal.ZERO : cashPaymentAmount; }
	public void setCashPaymentAmount(BigDecimal value) { cashPaymentAmount = value; }
	public BigDecimal getUpiPaymentAmount() { return upiPaymentAmount == null ? BigDecimal.ZERO : upiPaymentAmount; }
	public void setUpiPaymentAmount(BigDecimal value) { upiPaymentAmount = value; }
	public BigDecimal getCardPaymentAmount() { return cardPaymentAmount == null ? BigDecimal.ZERO : cardPaymentAmount; }
	public void setCardPaymentAmount(BigDecimal value) { cardPaymentAmount = value; }
	public BigDecimal getCreditCardPaymentAmount() { return creditCardPaymentAmount == null ? BigDecimal.ZERO : creditCardPaymentAmount; }
	public void setCreditCardPaymentAmount(BigDecimal value) { creditCardPaymentAmount = value; }
	public BigDecimal getDebitCardPaymentAmount() { return debitCardPaymentAmount == null ? BigDecimal.ZERO : debitCardPaymentAmount; }
	public void setDebitCardPaymentAmount(BigDecimal value) { debitCardPaymentAmount = value; }
	public Instant getPaymentDueAt() { return paymentDueAt; }
	public void setPaymentDueAt(Instant value) { paymentDueAt = value; }
	public Instant getPaymentReminderSentAt() { return paymentReminderSentAt; }
	public void setPaymentReminderSentAt(Instant value) { paymentReminderSentAt = value; }
	public String getBuyerName() { return buyerName; }
	public void setBuyerName(String value) { buyerName = value; }
	public Long getCustomerId() { return customerId; }
	public void setCustomerId(Long value) { customerId = value; }
	public String getBuyerGstin() { return buyerGstin; }
	public void setBuyerGstin(String value) { buyerGstin = value; }
	public String getInvoiceNumber() { return invoiceNumber; }
	public void setInvoiceNumber(String value) { invoiceNumber = value; }
	public LocalDateTime getSoldAt() { return soldAt; }
	public void setSoldAt(LocalDateTime value) { soldAt = value; }
	public BigDecimal getRevenue() { return revenue; }
	public void setRevenue(BigDecimal value) { revenue = value; }
	public BigDecimal getCost() { return cost; }
	public void setCost(BigDecimal value) { cost = value; }
	public List<SaleItem> getItems() { return items; }
	public void setItems(List<SaleItem> value) { items = value; }
}
