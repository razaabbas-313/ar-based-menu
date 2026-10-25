package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalyticsEventDto(
        @NotBlank String slug,
        Long dishId,
        @NotBlank String eventType
) {
}
