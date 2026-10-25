package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateDto(
        @NotBlank @Size(max = 100) String name,
        Integer displayOrder
) {
}
