package com.smartretail.auth.dto;

public class RegistrationData {

    private String shopName;
    private String shopAddress;
    private String state;
    private String city;
    private String postalCode;
    private Double locationLatitude;
    private Double locationLongitude;
    private String ownerName;
    private String ownerContact;
    private String ownerEmail;
    private String gstin;

    public RegistrationData() {
    }

    public RegistrationData(
            String shopName,
            String shopAddress,
            String ownerName,
            String ownerContact,
            String ownerEmail,
            String gstin,
            String state,
            String city,
            String postalCode,
            Double locationLatitude,
            Double locationLongitude
    ) {
        this.shopName = shopName;
        this.shopAddress = shopAddress;
        this.ownerName = ownerName;
        this.ownerContact = ownerContact;
        this.ownerEmail = ownerEmail;
        this.gstin = gstin;
        this.state = state;
        this.city = city;
        this.postalCode = postalCode;
        this.locationLatitude = locationLatitude;
        this.locationLongitude = locationLongitude;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getShopAddress() {
        return shopAddress;
    }

    public void setShopAddress(String shopAddress) {
        this.shopAddress = shopAddress;
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public Double getLocationLatitude() { return locationLatitude; }
    public void setLocationLatitude(Double locationLatitude) { this.locationLatitude = locationLatitude; }
    public Double getLocationLongitude() { return locationLongitude; }
    public void setLocationLongitude(Double locationLongitude) { this.locationLongitude = locationLongitude; }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerContact() {
        return ownerContact;
    }

    public void setOwnerContact(String ownerContact) {
        this.ownerContact = ownerContact;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }
}