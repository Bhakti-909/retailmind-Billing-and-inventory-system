package com.smartretail.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CustomerRequest {
    @NotBlank(message = "Customer name is required") private String name;
    @NotBlank(message = "Phone number is required") private String phone;
    @Email(message = "Email must be valid") private String email;
    private String address; private String notes;
    public String getName() { return name; } public void setName(String value) { name = value; } public String getPhone() { return phone; } public void setPhone(String value) { phone = value; }
    public String getEmail() { return email; } public void setEmail(String value) { email = value; } public String getAddress() { return address; } public void setAddress(String value) { address = value; }
    public String getNotes() { return notes; } public void setNotes(String value) { notes = value; }
}
