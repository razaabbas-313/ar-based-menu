package com.atelier.media.security;

public interface LoginThrottle {

    /**
     * Checks whether sign-in is allowed for the given key/email and client IP.
     * Throws {@link com.atelier.media.exception.TooManyAttemptsException} if locked out.
     */
    void checkAllowed(String key, String clientIp);

    default void checkAllowed(String key) {
        checkAllowed(key, "unknown");
    }

    /**
     * Records a failed sign-in attempt for key/email and client IP.
     * May introduce a progressive delay if consecutive failures reach thresholds.
     */
    void recordFailure(String key, String clientIp);

    default void recordFailure(String key) {
        recordFailure(key, "unknown");
    }

    /**
     * Records a successful sign-in attempt, resetting failure counter for this key and client IP.
     */
    void recordSuccess(String key, String clientIp);

    default void recordSuccess(String key) {
        recordSuccess(key, "unknown");
    }
}
