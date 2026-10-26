package com.smartretail.customer.controller;

import com.smartretail.customer.dto.CustomerRequest;
import com.smartretail.customer.dto.CustomerResponse;
import com.smartretail.customer.service.CustomerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }
    @GetMapping public List<CustomerResponse> all(HttpServletRequest request) { return service.all(businessId(request)); }
    @GetMapping("/{id}") public CustomerResponse one(@PathVariable Long id, HttpServletRequest request) { return service.one(id, businessId(request)); }
    @PostMapping public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest body, HttpServletRequest request) { return ResponseEntity.status(201).body(service.create(body, businessId(request))); }
    @PutMapping("/{id}") public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest body, HttpServletRequest request) { return service.update(id, body, businessId(request)); }
    @DeleteMapping("/{id}") public Map<String, String> delete(@PathVariable Long id, HttpServletRequest request) { service.delete(id, businessId(request)); return Map.of("message", "Customer deleted."); }
    private Long businessId(HttpServletRequest request) { return (Long) request.getAttribute("businessId"); }
}
