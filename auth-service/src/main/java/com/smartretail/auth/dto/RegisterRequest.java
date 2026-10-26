package com.smartretail.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public class RegisterRequest {

    @NotBlank
    private String shopName;

    private String shopAddress;

    @NotBlank(message = "Select your state.")
    private String state;

    @NotBlank(message = "Enter your city.")
    private String city;

    @NotBlank(message = "Enter your 6-digit PIN code.")
    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid 6-digit PIN code.")
    private String postalCode;

    @DecimalMin(value = "-90.0", message = "Invalid latitude.")
    @DecimalMax(value = "90.0", message = "Invalid latitude.")
    private Double locationLatitude;

    @DecimalMin(value = "-180.0", message = "Invalid longitude.")
    @DecimalMax(value = "180.0", message = "Invalid longitude.")
    private Double locationLongitude;

    @NotBlank
    private String ownerName;

    @NotBlank
    @Pattern(
        regexp = "^[0-9]{10}$",
        message = "Contact number must contain 10 digits"
    )
    private String ownerContact;

    @NotBlank
    @Email(message = "Enter a valid email address.")
    @Pattern(
        regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$",
        message = "Enter an email address with a valid domain, such as name@example.com."
    )
    private String ownerEmail;

    @Pattern(
        regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
        message = "Enter a valid 15-character GSTIN."
    )
    private String gstin;

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
        this.shopAddress = shopAddress == null ? null : shopAddress.trim();
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state == null ? null : state.trim();
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city == null ? null : city.trim();
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode == null ? null : postalCode.trim();
    }

    public Double getLocationLatitude() {
        return locationLatitude;
    }

    public void setLocationLatitude(Double locationLatitude) {
        this.locationLatitude = locationLatitude;
    }

    public Double getLocationLongitude() {
        return locationLongitude;
    }

    public void setLocationLongitude(Double locationLongitude) {
        this.locationLongitude = locationLongitude;
    }

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
        this.ownerContact = ownerContact == null ? null : ownerContact.trim();
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail == null ? null : ownerEmail.trim().toLowerCase();
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin == null || gstin.isBlank() ? null : gstin.replaceAll("\\s+", "").toUpperCase();
    }
}