package com.atelier.media.dto;

import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;

import java.time.Instant;

/** The signed-in owner's view of their restaurant account. */
public record RestaurantDto(Long id, String name, String slug, String ownerName, String email,
                            RestaurantStatus status, Instant createdAt, boolean mustChangePassword,
                            boolean canUpload3d) {

    public RestaurantDto(Long id, String name, String slug, String ownerName, String email,
                         RestaurantStatus status, Instant createdAt, boolean mustChangePassword) {
        this(id, name, slug, ownerName, email, status, createdAt, mustChangePassword, false);
    }

    public RestaurantDto(Long id, String name, String slug, String ownerName, String email,
                         RestaurantStatus status, Instant createdAt) {
        this(id, name, slug, ownerName, email, status, createdAt, false, false);
    }

    public static RestaurantDto from(Restaurant r) {
        return new RestaurantDto(r.getId(), r.getName(), r.getSlug(), r.getOwnerName(), r.getEmail(),
                r.getStatus(), r.getCreatedAt(), r.requiresPasswordChange(), Boolean.TRUE.equals(r.getCanUpload3d()));
    }
}
