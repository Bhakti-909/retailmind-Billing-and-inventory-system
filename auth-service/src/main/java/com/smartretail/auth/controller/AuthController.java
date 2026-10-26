package com.smartretail.auth.controller;

import com.smartretail.auth.dto.*;
import com.smartretail.auth.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegisterRequest request
    ) {

        authService.register(request);

        return ResponseEntity.ok(
                java.util.Map.of(
                        "message",
                        "OTP sent to your email."
                )
        );
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request
    ) {

        authService.verifyOtp(request);

        return ResponseEntity.ok(
                java.util.Map.of(
                        "message",
                        "Email verified successfully."
                )
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request
    ) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<?> requestPasswordReset(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        boolean accountExists = authService.requestPasswordReset(request.getEmail());
        if (!accountExists) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("message", "No active account exists with this email."));
        }
        return ResponseEntity.ok(java.util.Map.of(
                "message", "A verification code has been sent to your registered email. It expires in 5 minutes."
        ));
    }

    @PostMapping("/password/verify-otp")
    public ResponseEntity<?> verifyPasswordResetOtp(
            @Valid @RequestBody VerifyPasswordResetOtpRequest request
    ) {
        int attemptsRemaining = authService.verifyPasswordResetOtp(request.getEmail(), request.getOtp());
        if (attemptsRemaining >= 0) {
            String message = attemptsRemaining == 0
                    ? "The code is expired or the attempt limit was reached. Request a new code."
                    : "Incorrect code. " + attemptsRemaining + " verification attempts remain.";
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "message", message,
                    "attemptsRemaining", attemptsRemaining
            ));
        }
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Email code verified. You can now choose a new password."
        ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Your password has been updated. You can now sign in."
        ));
    }

   @GetMapping("/me")
   public ResponseEntity<?> getCurrentUser(
        Authentication authentication
  ) {
    String username = authentication.getName();

    return ResponseEntity.ok(
            authService.getCurrentUser(username)
    );
}

        @PutMapping("/business/gstin")
        public ResponseEntity<?> updateBusinessGstin(
                        @Valid @RequestBody UpdateGstinRequest request,
                        Authentication authentication
        ) {
                String gstin = authService.updateBusinessGstin(authentication.getName(), request);
                return ResponseEntity.ok(java.util.Map.of("gstin", gstin));
        }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(
                java.util.Map.of("message", "Password updated successfully.")
        );
    }
}
