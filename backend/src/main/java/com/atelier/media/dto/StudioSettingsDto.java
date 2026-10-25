package com.atelier.media.dto;

import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;

/** Everything the Brand & Menu Customizer shows, plus the account's usage counters. */
public record StudioSettingsDto(
        RestaurantBrandingDto branding,
        String ownerName,
        String email,
        RestaurantStatus status,
        long storageUsedBytes,
        int dishCount,
        int model3dCount,
        boolean canUpload3d
) {
    public StudioSettingsDto(RestaurantBrandingDto branding,
                             String ownerName,
                             String email,
                             RestaurantStatus status,
                             long storageUsedBytes,
                             int dishCount,
                             int model3dCount) {
        this(branding, ownerName, email, status, storageUsedBytes, dishCount, model3dCount, false);
    }

    public static StudioSettingsDto from(Restaurant r) {
        return new StudioSettingsDto(RestaurantBrandingDto.from(r), r.getOwnerName(), r.getEmail(), r.getStatus(),
                r.getStorageUsedBytes() == null ? 0 : r.getStorageUsedBytes(),
                r.getDishCount() == null ? 0 : r.getDishCount(),
                r.getModel3dCount() == null ? 0 : r.getModel3dCount(),
                Boolean.TRUE.equals(r.getCanUpload3d()));
    }
}
