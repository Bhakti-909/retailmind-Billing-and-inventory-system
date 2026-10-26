package com.smartretail.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerifyPasswordResetOtpRequest {
    @NotBlank(message = "Enter your registered email address.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "Enter the six-digit verification code.")
    @Pattern(regexp = "^[0-9]{6}$", message = "The verification code must contain 6 digits.")
    private String otp;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
