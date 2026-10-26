package com.smartretail.inventory.controller;

import com.smartretail.inventory.dto.ProductRequest;
import com.smartretail.inventory.dto.ProductResponse;
import com.smartretail.inventory.dto.StockDeductionRequest;
import com.smartretail.inventory.service.ProductService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/products")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:5175"})
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts(HttpServletRequest request) {
        return ResponseEntity.ok(productService.getAllProducts(businessId(request)));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts(HttpServletRequest request) {
        return ResponseEntity.ok(productService.getLowStockProducts(businessId(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(productService.getProductById(id, businessId(request)));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest productRequest, HttpServletRequest request) {
        ProductResponse created = productService.createProduct(productRequest, businessId(request));
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id, @Valid @RequestBody ProductRequest productRequest, HttpServletRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, productRequest, businessId(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id, HttpServletRequest request) {
        productService.deleteProduct(id, businessId(request));
        return ResponseEntity.ok(Map.of("message", "Product deleted successfully."));
    }

    @PostMapping("/decrement-stock")
    public ResponseEntity<List<ProductResponse>> decrementStock(
            @Valid @RequestBody StockDeductionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(productService.deductStock(body.items(), businessId(request)));
    }

    private Long businessId(HttpServletRequest request) {
        return (Long) request.getAttribute("businessId");
    }
}