// package com.smartretail.auth.dto;

// public class AuthResponse {

//     private String token;
//     private String username;
//     private String role;
//     private Long businessId;
//     private String shopName;

//     public AuthResponse(
//             String token,
//             String username,
//             String role,
//             Long businessId,
//             String shopName
//     ) {
//         this.token = token;
//         this.username = username;
//         this.role = role;
//         this.businessId = businessId;
//         this.shopName = shopName;
//     }

//     public String getToken() {
//         return token;
//     }

//     public String getUsername() {
//         return username;
//     }

//     public String getRole() {
//         return role;
//     }

//     public Long getBusinessId() {
//         return businessId;
//     }

//     public String getShopName() {
//         return shopName;
//     }
// }

package com.smartretail.auth.dto;

public class AuthResponse {

    private String token;
    private String username;
    private String role;
    private Long businessId;
    private String shopName;

    private String email;
    private String ownerName;
    private String contactNumber;
    private String shopAddress;
    private String gstin;
    private String state;
    private String city;
    private String postalCode;
    private Double locationLatitude;
    private Double locationLongitude;

    public AuthResponse(
            String token,
            String username,
            String role,
            Long businessId,
            String shopName,
            String email,
            String ownerName,
            String contactNumber,
            String shopAddress,
            String gstin,
            String state,
            String city,
            String postalCode,
            Double locationLatitude,
            Double locationLongitude
    ) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.businessId = businessId;
        this.shopName = shopName;
        this.email = email;
        this.ownerName = ownerName;
        this.contactNumber = contactNumber;
        this.shopAddress = shopAddress;
        this.gstin = gstin;
        this.state = state;
        this.city = city;
        this.postalCode = postalCode;
        this.locationLatitude = locationLatitude;
        this.locationLongitude = locationLongitude;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public String getShopName() {
        return shopName;
    }

    public String getEmail() {
        return email;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public String getShopAddress() {
        return shopAddress;
    }

    public String getGstin() {
        return gstin;
    }

    public String getState() { return state; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
    public Double getLocationLatitude() { return locationLatitude; }
    public Double getLocationLongitude() { return locationLongitude; }
}