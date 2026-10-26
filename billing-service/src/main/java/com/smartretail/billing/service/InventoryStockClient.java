package com.smartretail.billing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartretail.billing.dto.SaleRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Component
public class InventoryStockClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public InventoryStockClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${inventory.service.url:http://inventory-service:8082}") String inventoryServiceUrl) {
        this.restClient = builder.baseUrl(inventoryServiceUrl).build();
        this.objectMapper = objectMapper;
    }

    public void deductStock(String authorization, List<SaleRequest.Line> saleItems) {
        List<Map<String, Object>> items = saleItems.stream()
                .map(item -> Map.<String, Object>of(
                        "productId", item.productId(),
                        "quantity", item.quantity(),
                        "unitPrice", item.unitPrice(),
                        "unitCost", item.unitCost(),
                        "mrpPrice", item.mrpPrice() == null ? item.unitPrice() : item.mrpPrice(),
                        "gstRate", item.gstRate() == null ? BigDecimal.ZERO : item.gstRate()))
                .toList();
        try {
            restClient.post()
                    .uri("/api/inventory/products/decrement-stock")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(Map.of("items", items))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    exception.getStatusCode(),
                    inventoryErrorMessage(exception),
                    exception);
        }
    }

    private String inventoryErrorMessage(RestClientResponseException exception) {
        try {
            JsonNode response = objectMapper.readTree(exception.getResponseBodyAsString());
            JsonNode message = response.get("message");
            if (message != null && !message.isNull() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (JsonProcessingException ignored) {
        }
        return "Inventory could not be updated. Refresh stock and try again.";
    }
}