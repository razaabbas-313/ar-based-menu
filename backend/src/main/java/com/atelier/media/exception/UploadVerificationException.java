package com.atelier.media.exception;

/** The browser claimed an upload finished, but S3 does not agree. */
public class UploadVerificationException extends RuntimeException {
    public UploadVerificationException(String message) {
        super(message);
    }
}
