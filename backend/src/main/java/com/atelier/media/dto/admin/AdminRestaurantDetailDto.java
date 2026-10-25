package com.atelier.media.dto.admin;

import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.RestaurantBrandingDto;

import java.util.List;

/** Per-restaurant deep dive for the admin drawer. */
public record AdminRestaurantDetailDto(
        AdminRestaurantDto restaurant,
        RestaurantBrandingDto branding,
        long photoBytes,
        long modelBytes,
        long brandingBytes,
        long pendingUploads,
        List<MediaResponseDto> dishes,
        List<AuditEntryDto> recentAudit,
        String adminNotes
) {
}
