package com.atelier.media.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminModelApproveDto(
        @NotBlank(message = "S3 key is required")
        @Size(max = 512, message = "S3 key must not exceed 512 characters")
        String s3Key,
        String etag,
        String arPlacement
) {
}
