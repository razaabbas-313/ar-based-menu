package com.atelier.media.dto.admin;

import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;

import java.time.Instant;

/**
 * One row of the super-admin restaurant table.
 *
 * @param bandwidthTier      plan bucket derived from storage: Starter (&lt; 1 GB), Growth (&lt; 10 GB), Scale
 * @param mustChangePassword the owner has not yet replaced the temporary password
 */
public record AdminRestaurantDto(
        Long id,
        String name,
        String slug,
        String ownerName,
        String email,
        RestaurantStatus status,
        int dishCount,
        int model3dCount,
        long storageUsedBytes,
        String bandwidthTier,
        String logoUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        boolean mustChangePassword,
        String phoneNumber,
        String adminNotes,
        boolean isMenuLive,
        boolean canUpload3d
) {
    public AdminRestaurantDto(Long id, String name, String slug, String ownerName, String email,
                              RestaurantStatus status, int dishCount, int model3dCount, long storageUsedBytes,
                              String bandwidthTier, String logoUrl, Instant createdAt, Instant updatedAt,
                              Instant deletedAt, boolean mustChangePassword, String phoneNumber,
                              String adminNotes, boolean isMenuLive) {
        this(id, name, slug, ownerName, email, status, dishCount, model3dCount, storageUsedBytes,
                bandwidthTier, logoUrl, createdAt, updatedAt, deletedAt, mustChangePassword, phoneNumber,
                adminNotes, isMenuLive, false);
    }

    public AdminRestaurantDto(Long id, String name, String slug, String ownerName, String email,
                              RestaurantStatus status, int dishCount, int model3dCount, long storageUsedBytes,
                              String bandwidthTier, String logoUrl, Instant createdAt, Instant updatedAt,
                              Instant deletedAt, boolean mustChangePassword, String phoneNumber) {
        this(id, name, slug, ownerName, email, status, dishCount, model3dCount, storageUsedBytes,
                bandwidthTier, logoUrl, createdAt, updatedAt, deletedAt, mustChangePassword, phoneNumber,
                null, true, false);
    }

    private static final long GB = 1024L * 1024 * 1024;

    public static AdminRestaurantDto from(Restaurant r) {
        long storage = r.getStorageUsedBytes() == null ? 0 : r.getStorageUsedBytes();
        return new AdminRestaurantDto(r.getId(), r.getName(), r.getSlug(), r.getOwnerName(), r.getEmail(), r.getStatus(),
                r.getDishCount() == null ? 0 : r.getDishCount(),
                r.getModel3dCount() == null ? 0 : r.getModel3dCount(),
                storage, tierFor(storage), r.getLogoUrl(), r.getCreatedAt(), r.getUpdatedAt(), r.getDeletedAt(),
                r.requiresPasswordChange(), r.getPhoneNumber(), r.getAdminNotes(),
                !Boolean.FALSE.equals(r.getIsMenuLive()),
                Boolean.TRUE.equals(r.getCanUpload3d()));
    }

    public static String tierFor(long bytes) {
        if (bytes < GB) return "Starter";
        if (bytes < 10 * GB) return "Growth";
        return "Scale";
    }
}
