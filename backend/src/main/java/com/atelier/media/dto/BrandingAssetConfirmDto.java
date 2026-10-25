package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandingAssetConfirmDto(@NotBlank @Size(max = 512) String s3Key) {
}
