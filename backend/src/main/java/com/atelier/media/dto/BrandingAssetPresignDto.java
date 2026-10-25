package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Logo or banner upload request (image/webp, image/jpeg or image/png, compressed on-device). */
public record BrandingAssetPresignDto(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 64) String contentType,
        @NotNull @Positive Long uploadSizeBytes
) {
}
