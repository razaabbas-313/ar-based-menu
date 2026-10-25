package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Sent by the browser after S3 answered 200 OK to the PUT.
 * Size and ETag are re-verified server-side with a HEAD request; S3 is the source of truth.
 */
public record UploadConfirmDto(
        @NotBlank @Size(max = 512) String s3Key,
        @Size(max = 128) String etag,
        @Positive Long finalSizeBytes,
        @Positive Integer widthPx,
        @Positive Integer heightPx,
        Boolean hasArSupport
) {
}
