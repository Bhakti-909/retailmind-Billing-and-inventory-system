package com.smartretail.customer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers", uniqueConstraints = @UniqueConstraint(columnNames = {"business_id", "phone"}))
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "business_id", nullable = false) private Long businessId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String phone;
    private String email;
    private String address;
    private String notes;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
    public Long getId() { return id; } public Long getBusinessId() { return businessId; } public void setBusinessId(Long value) { businessId = value; }
    public String getName() { return name; } public void setName(String value) { name = value; } public String getPhone() { return phone; } public void setPhone(String value) { phone = value; }
    public String getEmail() { return email; } public void setEmail(String value) { email = value; } public String getAddress() { return address; } public void setAddress(String value) { address = value; }
    public String getNotes() { return notes; } public void setNotes(String value) { notes = value; } public LocalDateTime getCreatedAt() { return createdAt; } public LocalDateTime getUpdatedAt() { return updatedAt; }
}
