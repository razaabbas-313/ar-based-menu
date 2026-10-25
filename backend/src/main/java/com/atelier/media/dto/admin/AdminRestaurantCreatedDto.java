package com.atelier.media.dto.admin;

/**
 * @param temporaryPassword shown exactly once to the admin, who passes it to the owner; the owner
 *                          must replace it on first sign-in
 */
public record AdminRestaurantCreatedDto(AdminRestaurantDto restaurant, String temporaryPassword) {
}
