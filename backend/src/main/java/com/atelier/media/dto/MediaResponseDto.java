package com.atelier.media.dto;

import com.atelier.media.model.DishCategory;
import com.atelier.media.model.MediaType;
import com.atelier.media.model.UploadStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A dish as rendered by the guest menu and the studio.
 *
 * @param is3d                    true when a 3D model is attached ({@code modelUrl} set)
 * @param modelUrl                glTF/GLB URL for model-viewer, loaded only on demand by the guest UI
 * @param imageUrl                WebP/JPEG photo URL (null only for v2 "3D-only" dishes)
 * @param arPlacement             "table" (true-to-size) or "floor" (scalable pedestal)
 * @param arScale                 model-viewer ar-scale: "fixed" or "auto"
 * @param compressionSavedPercent 0-100 savings vs the original photo, or null if unknown
 * @param modelPending            an attach-3d upload is in flight
 */
public record MediaResponseDto(
        Long id,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String dishCode,
        String dishTitle,
        BigDecimal priceTag,
        DishCategory dishCategory,
        MediaType mediaType,
        UploadStatus uploadStatus,
        String contentType,
        String fileExtension,
        String originalFileName,
        String s3Key,
        String s3Url,
        boolean is3d,
        String modelUrl,
        String imageUrl,
        boolean hasArSupport,
        String arPlacement,
        String arScale,
        String arModes,
        Integer widthPx,
        Integer heightPx,
        Long fileSizeBytes,
        Long originalSizeBytes,
        Integer compressionSavedPercent,
        String etag,
        Instant createdAt,
        Instant completedAt,
        String description,
        List<String> dietaryTags,
        boolean available,
        boolean dailySpecial,
        Long modelFileSizeBytes,
        boolean modelPending,
        Long categoryId,
        String categoryName,
        String modelRequestStatus,
        String modelRequestNotes,
        Instant modelRequestedAt,
        Instant modelCompletedAt,
        Long viewsCount,
        Long modelViewsCount,
        Long arLaunchesCount
) {
    public MediaResponseDto(
            Long id,
            String dishTitle,
            BigDecimal priceTag,
            DishCategory dishCategory,
            MediaType mediaType,
            UploadStatus uploadStatus,
            String contentType,
            String fileExtension,
            String originalFileName,
            String s3Key,
            String s3Url,
            boolean is3d,
            String modelUrl,
            String imageUrl,
            boolean hasArSupport,
            String arPlacement,
            String arScale,
            String arModes,
            Integer widthPx,
            Integer heightPx,
            Long fileSizeBytes,
            Long originalSizeBytes,
            Integer compressionSavedPercent,
            String etag,
            Instant createdAt,
            Instant completedAt,
            String description,
            List<String> dietaryTags,
            boolean available,
            boolean dailySpecial,
            Long modelFileSizeBytes,
            boolean modelPending
    ) {
        this(id, id != null ? String.format("DSH-%04d", id) : "DSH-0001", dishTitle, priceTag, dishCategory, mediaType, uploadStatus, contentType, fileExtension,
                originalFileName, s3Key, s3Url, is3d, modelUrl, imageUrl, hasArSupport, arPlacement, arScale,
                arModes, widthPx, heightPx, fileSizeBytes, originalSizeBytes, compressionSavedPercent, etag,
                createdAt, completedAt, description, dietaryTags, available, dailySpecial, modelFileSizeBytes,
                modelPending, null, null, "NONE", null, null, null, 0L, 0L, 0L);
    }

    public MediaResponseDto restrictModes(boolean allow3d,boolean allowAr) {
        return new MediaResponseDto(
            id,
            dishCode,
            dishTitle,
            priceTag,
            dishCategory,
            mediaType,
            uploadStatus,
            contentType,
            fileExtension,
            originalFileName,
            s3Key,
            !allow3d && is3d && imageUrl == null ? null : s3Url,
            allow3d && is3d,
            allow3d ? modelUrl : null,
            imageUrl,
            allow3d && allowAr && hasArSupport,
            arPlacement,
            arScale,
            allowAr ? arModes : null,
            widthPx,
            heightPx,
            fileSizeBytes,
            originalSizeBytes,
            compressionSavedPercent,
            etag,
            createdAt,
            completedAt,
            description,
            dietaryTags,
            available,
            dailySpecial,
            modelFileSizeBytes,
            modelPending,
            categoryId,
            categoryName,
            modelRequestStatus,
            modelRequestNotes,
            modelRequestedAt,
            modelCompletedAt,
            viewsCount,
            modelViewsCount,
            arLaunchesCount);
    }
}
