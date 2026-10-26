package com.smartretail.auth.repository;

import com.smartretail.auth.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessRepository
        extends JpaRepository<Business, Long> {

    boolean existsByOwnerEmail(String ownerEmail);

    boolean existsByOwnerEmailIgnoreCase(String ownerEmail);

    boolean existsByOwnerContact(String ownerContact);

    Optional<Business> findByOwnerEmail(String ownerEmail);
}