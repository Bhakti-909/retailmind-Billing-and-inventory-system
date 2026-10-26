package com.smartretail.inventory.service;

import com.smartretail.inventory.dto.ProductRequest;
import com.smartretail.inventory.dto.ProductResponse;
import com.smartretail.inventory.dto.StockDeductionRequest;
import com.smartretail.inventory.entity.Product;
import com.smartretail.inventory.exception.DuplicateSkuException;
import com.smartretail.inventory.exception.ResourceNotFoundException;
import com.smartretail.inventory.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public List<ProductResponse> getAllProducts(Long businessId) {
        List<Product> products = productRepository.findAllByBusinessIdOrderByNameAsc(businessId);
        persistLegacyStockCounters(products);
        return products.stream().map(ProductResponse::fromEntity).toList();
    }

    @Transactional
    public ProductResponse getProductById(Long id, Long businessId) {
        Product product = findOwnedProduct(id, businessId);
        persistLegacyStockCounters(List.of(product));
        return ProductResponse.fromEntity(product);
    }

    @Transactional
    public List<ProductResponse> getLowStockProducts(Long businessId) {
        List<Product> products = productRepository.findLowStockByBusinessId(businessId);
        persistLegacyStockCounters(products);
        return products.stream().map(ProductResponse::fromEntity).toList();
    }

    public ProductResponse createProduct(ProductRequest request, Long businessId) {
        if (productRepository.existsByBusinessIdAndSkuIgnoreCase(businessId, request.getSku())) {
            throw new DuplicateSkuException("A product with SKU '" + request.getSku() + "' already exists.");
        }

        String barcode = normalizeBarcode(request.getBarcode());
        if (barcode != null && productRepository.existsByBusinessIdAndBarcodeIgnoreCase(businessId, barcode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product with this barcode already exists.");
        }

        Product product = new Product();
        product.setBusinessId(businessId);
        product.initializeStockCounters();
        applyRequest(product, request);

        return ProductResponse.fromEntity(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request, Long businessId) {
        Product product = productRepository.findByIdAndBusinessIdForUpdate(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.initializeStockCounters();

        if (productRepository.existsByBusinessIdAndSkuIgnoreCaseAndIdNot(businessId, request.getSku(), id)) {
            throw new DuplicateSkuException("A product with SKU '" + request.getSku() + "' already exists.");
        }

        String barcode = normalizeBarcode(request.getBarcode());
        if (barcode != null
                && productRepository.existsByBusinessIdAndBarcodeIgnoreCaseAndIdNot(businessId, barcode, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product with this barcode already exists.");
        }

        applyRequest(product, request);
        return ProductResponse.fromEntity(productRepository.save(product));
    }

    public void deleteProduct(Long id, Long businessId) {
        productRepository.delete(findOwnedProduct(id, businessId));
    }

    @Transactional
    public List<ProductResponse> deductStock(List<StockDeductionRequest.Line> requestedItems, Long businessId) {
        Map<Long, Integer> quantitiesByProduct = new TreeMap<>();
        for (StockDeductionRequest.Line item : requestedItems) {
            quantitiesByProduct.merge(item.productId(), item.quantity(), Integer::sum);
        }

        List<Product> lockedProducts = productRepository.findAllForUpdateByIdsAndBusinessId(
                quantitiesByProduct.keySet(), businessId);
        if (lockedProducts.size() != quantitiesByProduct.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more products are no longer available.");
        }

        for (Product product : lockedProducts) {
            product.initializeStockCounters();
            List<StockDeductionRequest.Line> productLines = requestedItems.stream()
                .filter(item -> item.productId().equals(product.getId()))
                .toList();
            StockDeductionRequest.Line firstLine = productLines.get(0);
            boolean priceChanged = product.getSellingPrice() == null || product.getCostPrice() == null
                || product.getSellingPrice().compareTo(firstLine.unitPrice()) != 0
                || product.getCostPrice().compareTo(firstLine.unitCost()) != 0;
            BigDecimal currentMrp = product.getMrpPrice() == null
                    ? product.getSellingPrice() : product.getMrpPrice();
            priceChanged = priceChanged || (firstLine.mrpPrice() != null
                    && (currentMrp == null || currentMrp.compareTo(firstLine.mrpPrice()) != 0));
                BigDecimal currentGstRate = product.getGstRate() == null ? BigDecimal.ZERO : product.getGstRate();
                priceChanged = priceChanged || (firstLine.gstRate() != null
                    && currentGstRate.compareTo(firstLine.gstRate()) != 0);
            if (priceChanged) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                product.getName() + " pricing changed. Refresh the bill and try again.");
            }

                if (product.getExpiryDate() != null && product.getExpiryDate().isBefore(LocalDate.now())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    product.getName() + " has expired and cannot be sold.");
                }

            int requestedQuantity = quantitiesByProduct.get(product.getId());
                if (product.getQuantity() == null || product.getQuantity() <= 0
                    || product.getQuantity() < requestedQuantity) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        product.getName() + " has only " + product.getQuantity() + " " + product.getUnit() + " in stock.");
            }
        }

        lockedProducts.forEach(product -> {
            int quantitySold = quantitiesByProduct.get(product.getId());
            product.setQuantity(product.getQuantity() - quantitySold);
            product.setSoldStock(product.getSoldStock() + quantitySold);
        });
        return productRepository.saveAll(lockedProducts).stream()
                .map(ProductResponse::fromEntity)
                .toList();
    }

    private Product findOwnedProduct(Long id, Long businessId) {
        return productRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private void persistLegacyStockCounters(List<Product> products) {
        List<Product> updated = products.stream()
            .filter(Product::initializeProductDefaults)
                .toList();
        if (!updated.isEmpty()) {
            productRepository.saveAll(updated);
        }
    }

    private String normalizeBarcode(String barcode) {
        return barcode == null || barcode.isBlank() ? null : barcode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setBarcode(normalizeBarcode(request.getBarcode()));
        product.setCategory(request.getCategory());
        product.setDescription(request.getDescription());
        product.setUnit(request.getUnit() == null || request.getUnit().isBlank() ? "pcs" : request.getUnit());
        product.setQuantity(request.getQuantity());
        product.setTotalStock(request.getQuantity() + product.getSoldStock());
        product.setReorderLevel(request.getReorderLevel() == null ? 5 : request.getReorderLevel());
        product.setCostPrice(request.getCostPrice());
        product.setSellingPrice(request.getSellingPrice());
        product.setMrpPrice(request.getMrpPrice() == null ? request.getSellingPrice() : request.getMrpPrice());
        BigDecimal gstRate = request.getGstRate() == null ? BigDecimal.ZERO : request.getGstRate();
        if (!isSupportedGstRate(gstRate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a supported GST rate: 0%, 5%, 12%, 18%, or 28%.");
        }
        product.setGstRate(gstRate);
        product.setSupplierName(request.getSupplierName());
        product.setExpiryDate(request.getExpiryDate());
    }

    private boolean isSupportedGstRate(BigDecimal rate) {
        return List.of("0", "5", "12", "18", "28").stream()
                .map(BigDecimal::new)
                .anyMatch(supported -> supported.compareTo(rate) == 0);
    }
}