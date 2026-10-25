package com.atelier.media.dto;

import java.time.Instant;

/**
 * @param expiresInSeconds token lifetime relative to now; clients should use this rather than
 *                         comparing {@code expiresAt} with a possibly skewed local clock
 * @param impersonatedBy   super-admin email when this is an impersonation session, else null
 * @param mustChangePassword true when the owner signed in with a temporary password and must set
 *                           a new one (POST /api/v1/auth/change-password) before using the studio
 */
public record AuthResponseDto(
        String token,
        String tokenType,
        Instant expiresAt,
        long expiresInSeconds,
        RestaurantDto restaurant,
        String impersonatedBy,
        boolean mustChangePassword
) {
    public AuthResponseDto(String token, String tokenType, Instant expiresAt, long expiresInSeconds, RestaurantDto restaurant) {
        this(token, tokenType, expiresAt, expiresInSeconds, restaurant, null, false);
    }

    public AuthResponseDto(String token, String tokenType, Instant expiresAt, long expiresInSeconds, RestaurantDto restaurant,
                           String impersonatedBy) {
        this(token, tokenType, expiresAt, expiresInSeconds, restaurant, impersonatedBy, false);
    }
}
