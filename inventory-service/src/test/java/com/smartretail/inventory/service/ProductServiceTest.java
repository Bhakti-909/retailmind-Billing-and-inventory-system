package com.smartretail.inventory.service;

import com.smartretail.inventory.dto.StockDeductionRequest;
import com.smartretail.inventory.entity.Product;
import com.smartretail.inventory.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void deductStockUpdatesRemainingAndSoldCounters() {
        Product product = product(5, null);
        when(productRepository.findAllForUpdateByIdsAndBusinessId(anyCollection(), eq(7L)))
                .thenReturn(List.of(product));
        when(productRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        productService.deductStock(List.of(line(2)), 7L);

        assertEquals(3, product.getQuantity());
        assertEquals(2, product.getSoldStock());
        assertEquals(5, product.getTotalStock());
        verify(productRepository).saveAll(anyList());
    }

    @Test
    void deductStockRejectsExpiredProductWithoutSaving() {
        Product product = product(5, LocalDate.now().minusDays(1));
        when(productRepository.findAllForUpdateByIdsAndBusinessId(anyCollection(), eq(7L)))
                .thenReturn(List.of(product));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> productService.deductStock(List.of(line(1)), 7L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Test product has expired and cannot be sold.", exception.getReason());
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    void deductStockRejectsProductWithNoRemainingStock() {
        Product product = product(0, null);
        when(productRepository.findAllForUpdateByIdsAndBusinessId(anyCollection(), eq(7L)))
                .thenReturn(List.of(product));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> productService.deductStock(List.of(line(1)), 7L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productRepository, never()).saveAll(anyList());
    }

    private Product product(int quantity, LocalDate expiryDate) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setBusinessId(7L);
        product.setName("Test product");
        product.setQuantity(quantity);
        product.setUnit("pcs");
        product.setCostPrice(new BigDecimal("5.00"));
        product.setSellingPrice(new BigDecimal("10.00"));
        product.setExpiryDate(expiryDate);
        return product;
    }

    private StockDeductionRequest.Line line(int quantity) {
        return new StockDeductionRequest.Line(1L, quantity,
            new BigDecimal("10.00"), new BigDecimal("5.00"), new BigDecimal("10.00"), BigDecimal.ZERO);
    }
}