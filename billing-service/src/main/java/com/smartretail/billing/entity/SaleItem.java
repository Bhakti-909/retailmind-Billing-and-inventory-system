package com.smartretail.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_items")
public class SaleItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String productName;

	@Column(nullable = false)
	private Integer quantity;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal unitCost;

	@Column(name = "mrp_price", precision = 12, scale = 2)
	private BigDecimal mrpPrice;

	@Column(name = "product_discount", precision = 12, scale = 2)
	private BigDecimal productDiscount;

	@Column(name = "bill_discount", precision = 12, scale = 2)
	private BigDecimal billDiscount;

	@Column(name = "taxable_amount", precision = 12, scale = 2)
	private BigDecimal taxableAmount;

	@Column(name = "gst_rate", precision = 5, scale = 2)
	private BigDecimal gstRate;

	@Column(name = "gst_amount", precision = 12, scale = 2)
	private BigDecimal gstAmount;

	public String getProductName() { return productName; }
	public void setProductName(String value) { productName = value; }
	public Integer getQuantity() { return quantity; }
	public void setQuantity(Integer value) { quantity = value; }
	public BigDecimal getUnitPrice() { return unitPrice; }
	public void setUnitPrice(BigDecimal value) { unitPrice = value; }
	public BigDecimal getUnitCost() { return unitCost; }
	public void setUnitCost(BigDecimal value) { unitCost = value; }
	public BigDecimal getMrpPrice() { return mrpPrice == null ? unitPrice : mrpPrice; }
	public void setMrpPrice(BigDecimal value) { mrpPrice = value; }
	public BigDecimal getProductDiscount() { return productDiscount == null ? BigDecimal.ZERO : productDiscount; }
	public void setProductDiscount(BigDecimal value) { productDiscount = value; }
	public BigDecimal getBillDiscount() { return billDiscount == null ? BigDecimal.ZERO : billDiscount; }
	public void setBillDiscount(BigDecimal value) { billDiscount = value; }
	public BigDecimal getTaxableAmount() { return taxableAmount == null ? BigDecimal.ZERO : taxableAmount; }
	public void setTaxableAmount(BigDecimal value) { taxableAmount = value; }
	public BigDecimal getGstRate() { return gstRate == null ? BigDecimal.ZERO : gstRate; }
	public void setGstRate(BigDecimal value) { gstRate = value; }
	public BigDecimal getGstAmount() { return gstAmount == null ? BigDecimal.ZERO : gstAmount; }
	public void setGstAmount(BigDecimal value) { gstAmount = value; }
	public Long getId() { return id; }
}
