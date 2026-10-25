package com.atelier.media.exception;

/** The client asked to upload something we do not accept (bad MIME, extension or size). */
public class InvalidMediaException extends RuntimeException {
    public InvalidMediaException(String message) {
        super(message);
    }
}
