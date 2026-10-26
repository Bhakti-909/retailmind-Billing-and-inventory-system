package com.smartretail.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartretail.auth.dto.RegistrationData;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private static final long OTP_TTL_MINUTES = 15;
    private static final long PASSWORD_RESET_OTP_TTL_MINUTES = 5;
    private static final long PASSWORD_RESET_SEND_WINDOW_HOURS = 2;
    private static final long PASSWORD_RESET_SEND_LIMIT = 3;

    private static final String OTP_PREFIX =
            "otp:";

    private static final String PASSWORD_RESET_PREFIX = "password-reset:otp:";
    private static final String PASSWORD_RESET_ATTEMPTS_PREFIX = "password-reset:attempts:";
    private static final String PASSWORD_RESET_SENDS_PREFIX = "password-reset:sends:";

    private static final String REGISTRATION_PREFIX =
            "registration:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom =
            new SecureRandom();

    public OtpService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public String generateAndStoreOtp(
            String email
    ) {

        String otp = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        String key =
                OTP_PREFIX +
                email.toLowerCase();

        redisTemplate.opsForValue().set(
                key,
                otp,
                OTP_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        return otp;
    }

    public String generateAndStorePasswordResetOtp(String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String otpKey = PASSWORD_RESET_PREFIX + normalizedEmail;
        String attemptsKey = PASSWORD_RESET_ATTEMPTS_PREFIX + normalizedEmail;
        // A resend invalidates the previous code and its attempt counter first.
        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(
                otpKey,
                passwordEncoder.encode(otp),
                PASSWORD_RESET_OTP_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        return otp;
    }

    /** Returns zero when allowed, otherwise retry-after minutes. */
    public long claimPasswordResetSend(String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String sendsKey = PASSWORD_RESET_SENDS_PREFIX + normalizedEmail;
        Long count = redisTemplate.opsForValue().increment(sendsKey);
        if (count == null) {
            throw new IllegalStateException("Unable to check password reset request limit.");
        }
        if (count == 1L) {
            redisTemplate.expire(sendsKey, PASSWORD_RESET_SEND_WINDOW_HOURS, TimeUnit.HOURS);
        }
        if (count <= PASSWORD_RESET_SEND_LIMIT) return 0;

        Long remainingSeconds = redisTemplate.getExpire(sendsKey, TimeUnit.SECONDS);
        if (remainingSeconds == null || remainingSeconds < 0) {
            return PASSWORD_RESET_SEND_WINDOW_HOURS * 60;
        }
        return Math.max(1, (remainingSeconds + 59) / 60);
    }

    public boolean verifyAndConsumePasswordResetOtp(String email, String otp) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String otpKey = PASSWORD_RESET_PREFIX + normalizedEmail;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) return false;
        if (storedOtp.startsWith("$2") && passwordEncoder.matches(otp, storedOtp)) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(PASSWORD_RESET_ATTEMPTS_PREFIX + normalizedEmail);
            return true;
        }

        recordPasswordResetOtpFailure(normalizedEmail);
        return false;
    }

    /** Returns -1 for a valid code, otherwise the number of attempts remaining. */
    public int verifyPasswordResetOtp(String email, String otp) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String storedOtp = redisTemplate.opsForValue().get(PASSWORD_RESET_PREFIX + normalizedEmail);
        if (storedOtp == null) return 0;
        if (storedOtp.startsWith("$2") && passwordEncoder.matches(otp, storedOtp)) return -1;

        return recordPasswordResetOtpFailure(normalizedEmail);
    }

    private int recordPasswordResetOtpFailure(String normalizedEmail) {
        String otpKey = PASSWORD_RESET_PREFIX + normalizedEmail;
        String attemptsKey = PASSWORD_RESET_ATTEMPTS_PREFIX + normalizedEmail;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, PASSWORD_RESET_OTP_TTL_MINUTES, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts >= 5L) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            return 0;
        }
        return attempts == null ? 0 : Math.max(0, 5 - attempts.intValue());
    }

    public void deletePasswordResetOtp(String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        redisTemplate.delete(PASSWORD_RESET_PREFIX + normalizedEmail);
        redisTemplate.delete(PASSWORD_RESET_ATTEMPTS_PREFIX + normalizedEmail);
    }

    public void storeRegistrationData(
            RegistrationData data
    ) {

        try {

            String key =
                    REGISTRATION_PREFIX +
                    data.getOwnerEmail()
                            .toLowerCase();

            String json =
                    objectMapper.writeValueAsString(data);

            redisTemplate.opsForValue().set(
                    key,
                    json,
                    OTP_TTL_MINUTES,
                    TimeUnit.MINUTES
            );

        } catch (JsonProcessingException e) {

            throw new RuntimeException(
                    "Unable to store registration data.",
                    e
            );
        }
    }

    public RegistrationData getRegistrationData(
            String email
    ) {

        try {

            String key =
                    REGISTRATION_PREFIX +
                    email.toLowerCase();

            String json =
                    redisTemplate
                            .opsForValue()
                            .get(key);

            if (json == null) {
                return null;
            }

            return objectMapper.readValue(
                    json,
                    RegistrationData.class
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to read registration data.",
                    e
            );
        }
    }

    public boolean verifyOtp(
            String email,
            String otp
    ) {

        String key =
                OTP_PREFIX +
                email.toLowerCase();

        String storedOtp =
                redisTemplate
                        .opsForValue()
                        .get(key);

        if (storedOtp == null) {
            return false;
        }

        if (!storedOtp.equals(otp)) {
            return false;
        }

        redisTemplate.delete(key);

        return true;
    }

    public void deleteRegistrationData(
            String email
    ) {

        String key =
                REGISTRATION_PREFIX +
                email.toLowerCase();

        redisTemplate.delete(key);
    }
}
