package com.smartretail.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;
import java.time.OffsetDateTime;

public record SaleRequest(
	@NotEmpty List<@Valid Line> items,
	boolean gstInvoice,
	@DecimalMin("0.00") BigDecimal discountAmount,
	@DecimalMin("0.00") @DecimalMax("28.00") BigDecimal gstRate,
	boolean intraState,
	String buyerName,
	@Min(1) Long customerId,
	@Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "Enter a valid buyer GSTIN.") String buyerGstin,
	@Pattern(regexp = "^(|Cash|Online|UPI|Card|Credit Card|Debit Card|Split|Balance)$", message = "Choose a supported payment method.") String paymentMethod,
	OffsetDateTime paymentDueAt,
	@Valid SplitPayment splitPayment
) {
	public record SplitPayment(
			@DecimalMin("0.00") BigDecimal cash,
			@DecimalMin("0.00") BigDecimal upi,
			@DecimalMin("0.00") BigDecimal creditCard,
			@DecimalMin("0.00") BigDecimal debitCard
	) { }

    public record Line(
	    @NotNull @Min(1) Long productId,
	    @NotBlank String productName,
	    @NotNull @Min(1) Integer quantity,
	    @NotNull @DecimalMin("0.0") BigDecimal unitPrice,
		    @NotNull @DecimalMin("0.0") BigDecimal unitCost,
	    @DecimalMin("0.0") BigDecimal mrpPrice,
	    @DecimalMin("0.00") @DecimalMax("28.00") BigDecimal gstRate
    ) { }
}
