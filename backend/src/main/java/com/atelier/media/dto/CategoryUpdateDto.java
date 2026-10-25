package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateDto(
        @NotBlank @Size(max = 100) String name,
        Integer displayOrder,
        Boolean isActive
) {
}
