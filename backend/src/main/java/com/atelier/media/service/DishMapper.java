package com.atelier.media.service;

import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.model.Media;
import com.atelier.media.model.MediaType;

/** Entity -> API representation for dishes. */
public final class DishMapper {

    public static final String AR_MODES = "webxr scene-viewer quick-look";

    private DishMapper() {
    }

    public static MediaResponseDto toDto(Media m) {
        return toDto(m, null);
    }

    public static MediaResponseDto toDto(Media m, String categoryName) {
        boolean legacyModelOnly = m.isLegacyModelOnly();
        boolean hasModel = m.hasModel();
        Integer saved = null;
        if (!legacyModelOnly && m.getOriginalSizeBytes() != null && m.getOriginalSizeBytes() > 0 && m.getFileSizeBytes() != null) {
            double ratio = 1.0 - (double) m.getFileSizeBytes() / m.getOriginalSizeBytes();
            saved = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 100);
        }
        return new MediaResponseDto(
                m.getId(),
                m.getDishCode(),
                m.getDishTitle(),
                m.getPriceTag(),
                m.getDishCategory(),
                m.getMediaType(),
                m.getUploadStatus(),
                m.getContentType(),
                m.getFileExtension(),
                m.getOriginalFileName(),
                m.getS3Key(),
                m.getS3Url(),
                hasModel,
                hasModel ? m.getModelS3Url() : null,
                legacyModelOnly ? null : m.getS3Url(),
                hasModel,
                m.getArPlacement(),
                Media.PLACEMENT_FLOOR.equals(m.getArPlacement()) ? "auto" : "fixed",
                hasModel ? AR_MODES : null,
                m.getWidthPx(),
                m.getHeightPx(),
                m.getFileSizeBytes(),
                m.getOriginalSizeBytes(),
                saved,
                m.getEtag(),
                m.getCreatedAt(),
                m.getCompletedAt(),
                m.getDescription(),
                DietaryTags.fromColumn(m.getDietaryTags()),
                !Boolean.FALSE.equals(m.getAvailable()),
                m.getMediaType() == MediaType.DAILY_SPECIAL,
                m.getModelFileSizeBytes() == null ? 0L : m.getModelFileSizeBytes(),
                m.getPendingModelS3Key() != null,
                m.getCategoryId(),
                categoryName,
                m.getModelRequestStatus() == null ? "NONE" : m.getModelRequestStatus(),
                m.getModelRequestNotes(),
                m.getModelRequestedAt(),
                m.getModelCompletedAt(),
                m.getViewsCount() == null ? 0L : m.getViewsCount(),
                m.getModelViewsCount() == null ? 0L : m.getModelViewsCount(),
                m.getArLaunchesCount() == null ? 0L : m.getArLaunchesCount()
        );
    }

    /** Guest presentation: strictly omits dishCode, s3Key, etag, notes and view counts. */
    public static MediaResponseDto toGuestDto(Media m, String categoryName) {
        MediaResponseDto full = toDto(m, categoryName);
        return new MediaResponseDto(
                full.id(),
                null, // dishCode omitted on guest menus
                full.dishTitle(),
                full.priceTag(),
                full.dishCategory(),
                full.mediaType(),
                full.uploadStatus(),
                full.contentType(),
                full.fileExtension(),
                null, // originalFileName
                null, // s3Key omitted
                full.s3Url(),
                full.is3d(),
                full.modelUrl(),
                full.imageUrl(),
                full.hasArSupport(),
                full.arPlacement(),
                full.arScale(),
                full.arModes(),
                full.widthPx(),
                full.heightPx(),
                full.fileSizeBytes(),
                full.originalSizeBytes(),
                full.compressionSavedPercent(),
                null, // etag omitted
                full.createdAt(),
                full.completedAt(),
                full.description(),
                full.dietaryTags(),
                full.available(),
                full.dailySpecial(),
                full.modelFileSizeBytes(),
                false, // modelPending
                full.categoryId(),
                full.categoryName(),
                null, // modelRequestStatus omitted
                null, // modelRequestNotes omitted
                null, // modelRequestedAt omitted
                null, // modelCompletedAt omitted
                0L,   // viewsCount omitted
                0L,   // modelViewsCount omitted
                0L    // arLaunchesCount omitted
        );
    }
}
