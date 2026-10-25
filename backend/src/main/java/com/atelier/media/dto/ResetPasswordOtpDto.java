package com.atelier.media.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordOtpDto(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 10) String otp,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
