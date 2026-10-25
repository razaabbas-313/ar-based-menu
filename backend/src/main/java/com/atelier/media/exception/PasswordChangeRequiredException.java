package com.atelier.media.exception;

/** The owner is still using the temporary password issued by the super admin. */
public class PasswordChangeRequiredException extends RuntimeException {
    public PasswordChangeRequiredException() {
        super("Set a new password to replace your temporary one before opening the studio.");
    }
}
