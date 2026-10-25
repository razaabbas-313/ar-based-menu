package com.atelier.media.exception;

/** The bearer token was valid but no longer maps to a usable restaurant account. */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
