package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Step 2 (POST /api/v1/media/{id}/replace-confirm), sent after S3 answered 200 to the PUT.
 * Size and ETag are re-verified with an S3 HEAD; S3 is the source of truth.
 */
public record ReplaceConfirmDto(
        @NotNull ReplaceAssetKind assetType,
        @NotBlank @Size(max = 512) String s3Key,
        @Size(max = 128) String etag,
        @Positive Integer widthPx,
        @Positive Integer heightPx
) {
}
