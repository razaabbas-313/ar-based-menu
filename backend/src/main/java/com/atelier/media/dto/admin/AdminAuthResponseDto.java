package com.atelier.media.dto.admin;

import java.time.Instant;

public record AdminAuthResponseDto(String token, String tokenType, Instant expiresAt, long expiresInSeconds, AdminProfileDto admin) {
}
