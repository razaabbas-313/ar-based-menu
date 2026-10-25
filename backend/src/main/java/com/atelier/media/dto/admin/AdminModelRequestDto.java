package com.atelier.media.dto.admin;

import java.time.Instant;
import java.util.List;

public record AdminModelRequestDto(
        Long dishId,
        Long restaurantId,
        String restaurantName,
        String restaurantSlug,
        String dishCode,
        String dishTitle,
        String photoUrl,
        String status,
        String notes,
        Instant requestedAt,
        Instant completedAt,
        List<PhotoSlotDto> photos
) {
    public AdminModelRequestDto(Long dishId, Long restaurantId, String restaurantName, String restaurantSlug,
                                String dishTitle, String photoUrl, String status, String notes,
                                Instant requestedAt, Instant completedAt, List<PhotoSlotDto> photos) {
        this(dishId, restaurantId, restaurantName, restaurantSlug, null, dishTitle, photoUrl, status, notes, requestedAt, completedAt, photos);
    }

    public record PhotoSlotDto(String slot, String s3Key, String s3Url) {}
}
