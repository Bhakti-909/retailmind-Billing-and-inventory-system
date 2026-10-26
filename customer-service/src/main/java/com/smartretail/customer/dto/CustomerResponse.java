package com.smartretail.customer.dto;

import com.smartretail.customer.entity.Customer;
import java.time.LocalDateTime;

public record CustomerResponse(Long id, String name, String phone, String email, String address, String notes, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static CustomerResponse from(Customer customer) { return new CustomerResponse(customer.getId(), customer.getName(), customer.getPhone(), customer.getEmail(), customer.getAddress(), customer.getNotes(), customer.getCreatedAt(), customer.getUpdatedAt()); }
}
