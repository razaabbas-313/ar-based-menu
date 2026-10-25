package com.atelier.media.dto;

import java.time.Instant;

public record ModelRequestHistoryDto(
        Long id,
        Long restaurantId,
        String restaurantName,
        String restaurantSlug,
        Long dishId,
        String dishCode,
        String dishTitle,
        String dishImageUrl,
        String status,
        String requestType,
        String referencePhotos,
        String notes,
        String adminNotes,
        String completedModelKey,
        String completedModelUrl,
        Instant requestedAt,
        Instant completedAt
) {
}
