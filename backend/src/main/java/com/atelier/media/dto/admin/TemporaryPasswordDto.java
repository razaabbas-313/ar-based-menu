package com.atelier.media.dto.admin;

/** Result of "Reset owner password": a new temporary password, shown once. */
public record TemporaryPasswordDto(AdminRestaurantDto restaurant, String temporaryPassword) {
}
