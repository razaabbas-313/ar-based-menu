package com.atelier.media.dto;

import java.util.Map;

public record ModelRequestPresignResponseDto(
        String slot,
        String uploadUrl,
        String objectKey,
        Map<String, String> requiredHeaders,
        long expiresInSeconds
) {
}
