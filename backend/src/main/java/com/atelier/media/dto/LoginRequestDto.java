package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(
        @NotBlank @Size(max = 160) String email,
        @NotBlank @Size(max = 72) String password
) {
}
