package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sent after S3 accepted the attach-3d PUT. */
public record ModelConfirmDto(@NotBlank @Size(max = 500) String s3Key, @Size(max = 128) String etag) {
}
