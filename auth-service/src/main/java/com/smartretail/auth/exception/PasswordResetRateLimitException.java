package com.smartretail.auth.exception;

public class PasswordResetRateLimitException extends RuntimeException {

    private final long retryAfterMinutes;

    public PasswordResetRateLimitException(long retryAfterMinutes) {
        super("You have requested the maximum of 3 reset codes in 2 hours. Try again in about "
                + retryAfterMinutes + " minute(s).");
        this.retryAfterMinutes = retryAfterMinutes;
    }

    public long getRetryAfterMinutes() {
        return retryAfterMinutes;
    }
}
