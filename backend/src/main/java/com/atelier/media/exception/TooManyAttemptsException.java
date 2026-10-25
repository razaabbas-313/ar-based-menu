package com.atelier.media.exception;

import java.time.Duration;

public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many failed sign-in attempts. Try again in " + Math.max(1, retryAfter.toMinutes()) + " minute(s).");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
