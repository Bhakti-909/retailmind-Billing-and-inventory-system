package com.smartretail.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "businesses")
public class BusinessProfile {

    @Id
    private Long id;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "shop_name")
    private String shopName;

    @Column(name = "shop_address")
    private String shopAddress;

    @Column(name = "state")
    private String state;

    @Column(name = "city")
    private String city;

    @Column(name = "postal_code", length = 6)
    private String postalCode;

    @Column(name = "owner_contact")
    private String ownerContact;

    @Column(name = "owner_email")
    private String ownerEmail;

    public Long getId() {
        return id;
    }

    public String getGstin() {
        return gstin;
    }

    public String getShopName() { return shopName; }

    public String getShopAddress() { return shopAddress; }

    public String getState() { return state; }

    public String getCity() { return city; }

    public String getPostalCode() { return postalCode; }

    public String getOwnerContact() { return ownerContact; }

    public String getOwnerEmail() { return ownerEmail; }
}
