package com.smartretail.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateGstinRequest {

    @NotBlank(message = "GSTIN is required.")
    @Pattern(
        regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$",
        message = "Enter a valid 15-character GSTIN."
    )
    private String gstin;

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin == null ? null : gstin.replaceAll("\\s+", "").toUpperCase();
    }
}