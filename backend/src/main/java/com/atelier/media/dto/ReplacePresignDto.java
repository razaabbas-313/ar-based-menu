package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Step 1 of an in-place media replacement (POST /api/v1/media/{id}/replace-presign).
 *
 * @param originalSizeBytes size of the photo before on-device compression (PHOTO only, optional)
 * @param arPlacement       "table" or "floor" (MODEL only, optional)
 */
public record ReplacePresignDto(
        @NotNull ReplaceAssetKind assetType,
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 64) String contentType,
        @NotNull @Positive Long uploadSizeBytes,
        @Positive Long originalSizeBytes,
        @Pattern(regexp = "table|floor") String arPlacement
) {
}
