package com.atelier.media.exception;

/** The restaurant exists but the platform owner has suspended it. */
public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException() {
        super("This restaurant account is suspended. Please contact the platform owner.");
    }
}
