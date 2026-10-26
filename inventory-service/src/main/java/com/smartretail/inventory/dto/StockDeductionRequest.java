package com.smartretail.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.List;

public record StockDeductionRequest(
        @NotEmpty List<@Valid Line> items
) {
    public record Line(
            @NotNull @Min(1) Long productId,
            @NotNull @Min(1) Integer quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
                        @NotNull @DecimalMin("0.00") BigDecimal unitCost,
                            @DecimalMin("0.00") BigDecimal mrpPrice,
                            @DecimalMin("0.00") BigDecimal gstRate
    ) { }
}