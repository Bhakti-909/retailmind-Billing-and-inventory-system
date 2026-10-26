package com.smartretail.inventory.dto;
import com.smartretail.inventory.entity.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProductResponse {

    private Long id;
    private String name;
    private String sku;
    private String barcode;
    private String category;
    private String description;
    private String unit;
    private Integer quantity;
    private Integer totalStock;
    private Integer soldStock;
    private Integer reorderLevel;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal mrpPrice;
    private BigDecimal gstRate;
    private BigDecimal taxablePrice;
    private BigDecimal gstAmount;
    private String supplierName;
    private LocalDate expiryDate;
    private boolean lowStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse fromEntity(Product product) {
        ProductResponse response = new ProductResponse();
        response.id = product.getId();
        response.name = product.getName();
        response.sku = product.getSku();
        response.barcode = product.getBarcode();
        response.category = product.getCategory();
        response.description = product.getDescription();
        response.unit = product.getUnit();
        response.quantity = product.getQuantity();
        response.totalStock = product.getTotalStock() == null
            ? product.getQuantity() : product.getTotalStock();
        response.soldStock = product.getSoldStock() == null ? 0 : product.getSoldStock();
        response.reorderLevel = product.getReorderLevel();
        response.costPrice = product.getCostPrice();
        response.sellingPrice = product.getSellingPrice();
        response.mrpPrice = product.getMrpPrice() == null ? product.getSellingPrice() : product.getMrpPrice();
        response.gstRate = product.getGstRate() == null ? BigDecimal.ZERO : product.getGstRate();
        BigDecimal grossPrice = product.getSellingPrice() == null ? BigDecimal.ZERO : product.getSellingPrice();
        BigDecimal taxDivisor = BigDecimal.valueOf(100).add(response.gstRate);
        response.taxablePrice = response.gstRate.signum() == 0
            ? grossPrice.setScale(2, RoundingMode.HALF_UP)
            : grossPrice.multiply(BigDecimal.valueOf(100)).divide(taxDivisor, 2, RoundingMode.HALF_UP);
        response.gstAmount = grossPrice.subtract(response.taxablePrice).setScale(2, RoundingMode.HALF_UP);
        response.supplierName = product.getSupplierName();
        response.expiryDate = product.getExpiryDate();
        response.lowStock = product.getQuantity() != null
                && product.getReorderLevel() != null
                && product.getQuantity() <= product.getReorderLevel();
        response.createdAt = product.getCreatedAt();
        response.updatedAt = product.getUpdatedAt();
        return response;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getBarcode() { return barcode; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public Integer getQuantity() { return quantity; }
    public Integer getTotalStock() { return totalStock; }
    public Integer getSoldStock() { return soldStock; }
    public Integer getReorderLevel() { return reorderLevel; }
    public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public BigDecimal getMrpPrice() { return mrpPrice; }
    public BigDecimal getGstRate() { return gstRate; }
    public BigDecimal getTaxablePrice() { return taxablePrice; }
    public BigDecimal getGstAmount() { return gstAmount; }
    public String getSupplierName() { return supplierName; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public boolean isLowStock() { return lowStock; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}