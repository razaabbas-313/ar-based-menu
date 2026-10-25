package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Restaurant owner replaces the temporary password (or simply changes it later). */
public record ChangeOwnPasswordDto(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String newPassword
) {
}
