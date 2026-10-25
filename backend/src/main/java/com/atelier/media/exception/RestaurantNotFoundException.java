package com.atelier.media.exception;

public class RestaurantNotFoundException extends RuntimeException {
    public RestaurantNotFoundException(String slug) {
        super("No restaurant is published at '" + slug + "'.");
    }

    public RestaurantNotFoundException(Long id) {
        super("No restaurant found with id '" + id + "'.");
    }
}
