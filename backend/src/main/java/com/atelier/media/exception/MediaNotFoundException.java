package com.atelier.media.exception;

public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(String message) {
        super(message);
    }

    public MediaNotFoundException(Long id) {
        super("Dish not found: " + id);
    }
}
