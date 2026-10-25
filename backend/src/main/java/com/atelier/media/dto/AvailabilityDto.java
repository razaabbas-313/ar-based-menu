package com.atelier.media.dto;

import jakarta.validation.constraints.NotNull;

/** Quick "In stock / Out of stock" toggle. */
public record AvailabilityDto(@NotNull Boolean available) {
}
