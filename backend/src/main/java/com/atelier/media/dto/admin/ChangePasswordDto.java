package com.atelier.media.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordDto(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters") String newPassword
) {
}
