package com.atelier.media.dto.admin;

import com.atelier.media.model.RestaurantStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RestaurantStatusUpdateDto(@NotNull RestaurantStatus status, @Size(max = 300) String reason) {
}
