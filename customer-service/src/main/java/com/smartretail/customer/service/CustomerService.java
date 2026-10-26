package com.smartretail.customer.service;

import com.smartretail.customer.dto.CustomerRequest;
import com.smartretail.customer.dto.CustomerResponse;
import com.smartretail.customer.entity.Customer;
import com.smartretail.customer.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class CustomerService {
    private final CustomerRepository repository;
    public CustomerService(CustomerRepository repository) { this.repository = repository; }
    public List<CustomerResponse> all(Long businessId) { return repository.findAllByBusinessIdOrderByNameAsc(businessId).stream().map(CustomerResponse::from).toList(); }
    public CustomerResponse one(Long id, Long businessId) { return CustomerResponse.from(owned(id, businessId)); }
    public CustomerResponse create(CustomerRequest request, Long businessId) {
        if (repository.existsByBusinessIdAndPhone(businessId, request.getPhone().trim())) throw new ResponseStatusException(HttpStatus.CONFLICT, "A customer with this phone number already exists.");
        Customer customer = new Customer(); customer.setBusinessId(businessId); apply(customer, request); return CustomerResponse.from(repository.save(customer));
    }
    public CustomerResponse update(Long id, CustomerRequest request, Long businessId) {
        if (repository.existsByBusinessIdAndPhoneAndIdNot(businessId, request.getPhone().trim(), id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "A customer with this phone number already exists.");
        Customer customer = owned(id, businessId); apply(customer, request); return CustomerResponse.from(repository.save(customer));
    }
    public void delete(Long id, Long businessId) { repository.delete(owned(id, businessId)); }
    private Customer owned(Long id, Long businessId) { return repository.findByIdAndBusinessId(id, businessId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found.")); }
    private void apply(Customer customer, CustomerRequest request) { customer.setName(request.getName().trim()); customer.setPhone(request.getPhone().trim()); customer.setEmail(blankToNull(request.getEmail())); customer.setAddress(blankToNull(request.getAddress())); customer.setNotes(blankToNull(request.getNotes())); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
