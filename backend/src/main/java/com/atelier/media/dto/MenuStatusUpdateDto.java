package com.atelier.media.dto;

import jakarta.validation.constraints.NotNull;

public record MenuStatusUpdateDto(@NotNull Boolean isMenuLive) {
}
