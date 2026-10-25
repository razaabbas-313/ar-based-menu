package com.atelier.media.dto.admin;

import com.atelier.media.model.PlatformUser;

import java.time.Instant;

public record AdminProfileDto(Long id, String email, String displayName, boolean mustChangePassword, Instant lastLoginAt) {

    public static AdminProfileDto from(PlatformUser u) {
        return new AdminProfileDto(u.getId(), u.getEmail(), u.getDisplayName(),
                Boolean.TRUE.equals(u.getMustChangePassword()), u.getLastLoginAt());
    }
}
