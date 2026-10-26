package com.smartretail.auth.service;

import com.smartretail.auth.dto.*;
import com.smartretail.auth.entity.Business;
import com.smartretail.auth.entity.User;
import com.smartretail.auth.repository.BusinessRepository;
import com.smartretail.auth.repository.UserRepository;
import com.smartretail.auth.exception.PasswordResetRateLimitException;
import com.smartretail.auth.security.JwtService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.util.Locale;

@Service
public class AuthService {

        private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;

    private final OtpService otpService;
    private final EmailService emailService;

    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserCacheService userCacheService;

    private final SecureRandom random =
            new SecureRandom();

    public AuthService(
            BusinessRepository businessRepository,
            UserRepository userRepository,
            OtpService otpService,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserCacheService userCacheService
    ) {

        this.businessRepository =
                businessRepository;

        this.userRepository =
                userRepository;

        this.otpService =
                otpService;

        this.emailService =
                emailService;

        this.passwordEncoder =
                passwordEncoder;

        this.jwtService =
                jwtService;

        this.userCacheService = userCacheService;
    }

    /*
     * STEP 1
     * Registration request
     */
    public void register(
            RegisterRequest request
    ) {

        String email =
                request.getOwnerEmail()
                        .trim()
                        .toLowerCase();

        if (businessRepository.existsByOwnerEmailIgnoreCase(email)) {

            throw new RuntimeException(
                    "This email is already registered."
            );
        }

        if (businessRepository.existsByOwnerContact(request.getOwnerContact())) {
            throw new RuntimeException(
                    "This mobile number is already registered."
            );
        }

        RegistrationData data =
                new RegistrationData(
                        request.getShopName(),
                        request.getShopAddress() == null ? "" : request.getShopAddress(),
                        request.getOwnerName(),
                        request.getOwnerContact(),
                        email,
                        request.getGstin(),
                        request.getState(),
                        request.getCity(),
                        request.getPostalCode(),
                        request.getLocationLatitude(),
                        request.getLocationLongitude()
                );

        /*
         * Store registration data
         * in Redis for 15 minutes.
         */
        otpService.storeRegistrationData(
                data
        );

        /*
         * Generate OTP
         * and store it in Redis
         * for 15 minutes.
         */
        String otp =
                otpService.generateAndStoreOtp(
                        email
                );

        /*
         * Send OTP to owner Gmail.
         */
        emailService.sendOtpEmail(
                email,
                otp
        );
    }

    /*
     * STEP 2
     * Verify OTP
     *
     * OTP successful झाल्यावर:
     *
     * Redis registration data
     *       ↓
     * PostgreSQL Business
     *       ↓
     * PostgreSQL User
     *       ↓
     * Credentials email
     */
    @Transactional
    public void verifyOtp(
            VerifyOtpRequest request
    ) {

        String email =
                request.getEmail()
                        .trim()
                        .toLowerCase();

        boolean verified =
                otpService.verifyOtp(
                        email,
                        request.getOtp()
                );

        if (!verified) {

            throw new RuntimeException(
                    "Invalid or expired OTP."
            );
        }

        RegistrationData data =
                otpService.getRegistrationData(
                        email
                );

        if (data == null) {

            throw new RuntimeException(
                    "Registration session expired. Please register again."
            );
        }

        /*
         * Double-check email.
         */
        if (!email.equals(
                data.getOwnerEmail()
        )) {

            throw new RuntimeException(
                    "Invalid registration data."
            );
        }

                if (businessRepository.existsByOwnerEmailIgnoreCase(email)) {
                        throw new RuntimeException(
                                        "This email is already registered. Please sign in instead."
                        );
                }

                if (businessRepository.existsByOwnerContact(data.getOwnerContact())) {
                        throw new RuntimeException(
                                        "This mobile number is already registered."
                        );
                }

        /*
         * Create Business.
         */
        Business business =
                new Business();

        business.setShopName(
                data.getShopName()
        );

        business.setShopAddress(
                data.getShopAddress() == null ? "" : data.getShopAddress()
        );
        business.setState(data.getState());
        business.setCity(data.getCity());
        business.setPostalCode(data.getPostalCode());
        business.setLocationLatitude(data.getLocationLatitude());
        business.setLocationLongitude(data.getLocationLongitude());

        business.setOwnerName(
                data.getOwnerName()
        );

        business.setOwnerContact(
                data.getOwnerContact()
        );

        business.setOwnerEmail(
                data.getOwnerEmail()
        );

        business.setGstin(data.getGstin());
        business.setState(data.getState());
        business.setCity(data.getCity());
        business.setPostalCode(data.getPostalCode());
        business.setLocationLatitude(data.getLocationLatitude());
        business.setLocationLongitude(data.getLocationLongitude());

        Business savedBusiness =
                businessRepository.save(
                        business
                );

        /*
         * Generate User ID.
         */
        String username =
                generateUsername();

        /*
         * Generate temporary password.
         */
        String plainPassword =
                generatePassword();

        /*
         * NEVER store plain password.
         *
         * Only BCrypt hash is stored.
         */
        String passwordHash =
                passwordEncoder.encode(
                        plainPassword
                );

        /*
         * Create User.
         */
        User user =
                new User();

        user.setBusiness(
                savedBusiness
        );

        user.setUsername(
                username
        );

        user.setPasswordHash(
                passwordHash
        );

        user.setEmail(
                data.getOwnerEmail()
        );

        user.setRole(
                "OWNER"
        );

        user.setEnabled(
                true
        );

        userRepository.save(user);

        /*
         * Delete temporary registration
         * information from Redis.
         */
        otpService.deleteRegistrationData(
                email
        );

        /*
         * Send User ID + Password
         * to same Gmail.
         */
        emailService.sendCredentialsEmail(
                email,
                username,
                plainPassword
        );

        emailService.sendRegistrationSuccessEmail(
        email,
        data.getOwnerName(),
        data.getShopName()
        );
    }

    /*
     * LOGIN
     */
    public AuthResponse login(
            LoginRequest request
    ) {

        User user =
                userRepository
                        .findByUsernameIgnoreCaseOrEmailIgnoreCase(
                                request.getUsername().trim(),
                                request.getUsername().trim()
                        )
                        .orElseThrow(
                                () -> new BadCredentialsException(
                                        "Invalid User ID, email, or password."
                                )
                        );

        if (!user.isEnabled()) {

            throw new DisabledException(
                    "User account is disabled."
            );
        }

        boolean passwordMatches =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPasswordHash()
                );

        if (!passwordMatches) {

            throw new BadCredentialsException(
                    "Invalid User ID, email, or password."
            );
        }

        String token =
                jwtService.generateToken(
                        user.getUsername(),
                        user.getBusiness().getId(),
                        user.getRole()
                );

        return new AuthResponse(
                token,
                user.getUsername(),
                user.getRole(),
                user.getBusiness().getId(),
                user.getBusiness().getShopName(),
                user.getEmail(),
                user.getBusiness().getOwnerName(),
                user.getBusiness().getOwnerContact(),
                user.getBusiness().getShopAddress(),
                user.getBusiness().getGstin(),
                user.getBusiness().getState(),
                user.getBusiness().getCity(),
                user.getBusiness().getPostalCode(),
                user.getBusiness().getLocationLatitude(),
                user.getBusiness().getLocationLongitude()
);
    }

    /*
     * User ID
     */
    private String generateUsername() {

        String username;

        do {

            username =
                    "SRT-" +
                    (100000 +
                            random.nextInt(900000));

        } while (
                userRepository
                        .existsByUsername(username)
        );

        return username;
    }

    /*
     * Temporary plain password.
     *
     * This is ONLY sent by email.
     * Database gets BCrypt hash.
     */
    private String generatePassword() {

        String chars =
                "ABCDEFGHJKLMNPQRSTUVWXYZ" +
                "abcdefghijkmnopqrstuvwxyz" +
                "23456789@#$%";

        StringBuilder password =
                new StringBuilder();

        for (int i = 0; i < 10; i++) {

            password.append(
                    chars.charAt(
                            random.nextInt(
                                    chars.length()
                            )
                    )
            );
        }

        return password.toString();
    }

    public AuthResponse getCurrentUser(String username) {

    User user = userRepository
            .findByUsername(username)
            .orElseThrow(
                    () -> new BadCredentialsException(
                            "User not found."
                    )
            );

    if (!user.isEnabled()) {
        throw new DisabledException(
                "User account is disabled."
        );
    }

    return new AuthResponse(
        null,
        user.getUsername(),
        user.getRole(),
        user.getBusiness().getId(),
        user.getBusiness().getShopName(),
        user.getEmail(),
        user.getBusiness().getOwnerName(),
        user.getBusiness().getOwnerContact(),
                user.getBusiness().getShopAddress(),
        user.getBusiness().getGstin(),
        user.getBusiness().getState(),
        user.getBusiness().getCity(),
        user.getBusiness().getPostalCode(),
        user.getBusiness().getLocationLatitude(),
        user.getBusiness().getLocationLongitude()
   );
}

        public boolean requestPasswordReset(String email) {
                String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
                User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                                .filter(User::isEnabled)
                                .orElse(null);
                if (user == null) return false;

                String registeredEmail = user.getEmail().trim();
                long retryAfterMinutes = otpService.claimPasswordResetSend(registeredEmail);
                if (retryAfterMinutes > 0) {
                        throw new PasswordResetRateLimitException(retryAfterMinutes);
                }
                String otp = otpService.generateAndStorePasswordResetOtp(registeredEmail);
                try {
                        emailService.sendPasswordResetOtpEmail(
                                        registeredEmail, otp,
                                        user.getBusiness().getOwnerName(),
                                        user.getBusiness().getShopName());
                } catch (MailException exception) {
                        otpService.deletePasswordResetOtp(registeredEmail);
                        logger.error("Password reset email delivery failed.", exception);
                        throw exception;
                }
                return true;
        }

        public int verifyPasswordResetOtp(String email, String otp) {
                String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
                User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                                .filter(User::isEnabled)
                                .orElseThrow(() -> new IllegalArgumentException("No active account exists with this email."));
                return otpService.verifyPasswordResetOtp(user.getEmail(), otp);
        }

        @Transactional
        public void resetPassword(ResetPasswordRequest request) {
                String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
                User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                                .filter(User::isEnabled)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "The verification code is invalid or expired. Request a new code."));

                if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
                        throw new IllegalArgumentException("Choose a new password different from your current password.");
                }
                if (!otpService.verifyAndConsumePasswordResetOtp(user.getEmail(), request.getOtp())) {
                        throw new IllegalArgumentException(
                                        "The verification code is invalid, expired, or has been entered too many times.");
                }

                user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                userRepository.save(user);
                sendPasswordChangedNotificationAfterCommit(user);
        }

        @Transactional
        public String updateBusinessGstin(String username, UpdateGstinRequest request) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new BadCredentialsException("User not found."));

                if (!user.isEnabled()) {
                        throw new DisabledException("User account is disabled.");
                }

                String gstin = request.getGstin().replaceAll("\\s+", "").toUpperCase();
                user.getBusiness().setGstin(gstin);
                // Flush before returning so a database/schema constraint error is
                // reported by this request instead of appearing as a false success.
                Business savedBusiness = businessRepository.saveAndFlush(user.getBusiness());
                return savedBusiness.getGstin();
        }

        @Transactional
        public void changePassword(String username, ChangePasswordRequest request) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new BadCredentialsException("User not found."));

                if (!user.isEnabled()) {
                        throw new DisabledException("User account is disabled.");
                }

                if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                        throw new BadCredentialsException("Current password is incorrect.");
                }

                if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
                        throw new IllegalArgumentException("Choose a new password different from your current password.");
                }

                user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                userRepository.save(user);
                sendPasswordChangedNotificationAfterCommit(user);
        }

        private void sendPasswordChangedNotificationAfterCommit(User user) {
                String email = user.getEmail();
                String ownerName = user.getBusiness().getOwnerName();
                String shopName = user.getBusiness().getShopName();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                                try {
                                        emailService.sendPasswordChangedEmail(email, ownerName, shopName);
                                } catch (MailException exception) {
                                        logger.warn("Password changed, but the owner notification email could not be sent.", exception);
                                }
                        }
                });
        }
}
