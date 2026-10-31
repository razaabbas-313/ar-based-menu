package com.atelier.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@jakarta.persistence.EntityListeners(com.atelier.media.service.AssetCleanupListener.class)
@Table(name = "media_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {
    @jakarta.persistence.Transient
    private java.util.Set<String> cleanupSnapshot;


    public static final String PLACEMENT_TABLE = "table";
    public static final String PLACEMENT_FLOOR = "floor";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant. Every staff query filters on this column. */
    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private Long restaurantId;

    /** Auto-generated, unmodifiable dish reference identifier (e.g. DSH-0001). */
    @Column(name = "dish_code", nullable = false, updatable = false, length = 32)
    private String dishCode;

    /** Optimistic lock: a concurrent confirm and reaper cannot both win. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_file_name", nullable = false, length = 120)
    private String storedFileName;

    @Column(name = "s3_bucket", nullable = false, length = 63)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, length = 512, unique = true)
    private String s3Key;

    @Column(name = "s3_url", nullable = false, length = 1024)
    private String s3Url;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "file_extension", nullable = false, length = 10)
    private String fileExtension;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "original_size_bytes")
    private Long originalSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private UploadStatus uploadStatus;

    @Column(name = "dish_title", nullable = false, length = 160)
    private String dishTitle;

    @Column(name = "price_tag", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "dish_category", nullable = false, length = 20)
    private DishCategory dishCategory;

    @Builder.Default
    @Column(name = "has_ar_support", nullable = false)
    private Boolean hasArSupport = Boolean.FALSE;

    @Builder.Default
    @Column(name = "ar_placement", nullable = false, length = 10)
    private String arPlacement = PLACEMENT_TABLE;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "etag", length = 128)
    private String etag;

    @Column(name = "uploaded_by_user_id", length = 64)
    private String uploadedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // ---- v3: optional 3D model attached to the dish (the primary s3_key is the 2D photo) ----

    @Column(name = "model_s3_key", length = 500, unique = true)
    private String modelS3Key;

    @Column(name = "model_s3_url", columnDefinition = "TEXT")
    private String modelS3Url;

    @Builder.Default
    @Column(name = "model_file_size_bytes", nullable = false)
    private Long modelFileSizeBytes = 0L;

    /** A .glb presigned via attach-3d but not confirmed yet. */
    @Column(name = "pending_model_s3_key", length = 500)
    private String pendingModelS3Key;

    @Column(name = "pending_model_size_bytes")
    private Long pendingModelSizeBytes;

    @Column(name = "pending_model_content_type", length = 64)
    private String pendingModelContentType;

    @Column(name = "pending_model_created_at")
    private Instant pendingModelCreatedAt;

    // ---- v4: replacement photo presigned via replace-presign but not confirmed yet ----

    @Column(name = "pending_image_s3_key", length = 512)
    private String pendingImageS3Key;

    @Column(name = "pending_image_size_bytes")
    private Long pendingImageSizeBytes;

    @Column(name = "pending_image_original_size_bytes")
    private Long pendingImageOriginalSizeBytes;

    @Column(name = "pending_image_content_type", length = 64)
    private String pendingImageContentType;

    @Column(name = "pending_image_created_at")
    private Instant pendingImageCreatedAt;

    // ---- v3: menu metadata ----

    @Builder.Default
    @Column(name = "is_available", nullable = false)
    private Boolean available = Boolean.TRUE;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Comma-separated, lower-case, from {@code DietaryTags.ALLOWED}. */
    @Column(name = "dietary_tags", length = 255)
    private String dietaryTags;

    // ---- v5: custom category & 3D concierge pipeline ----

    @Column(name = "category_id")
    private Long categoryId;

    @Builder.Default
    @Column(name = "model_request_status", nullable = false, length = 30)
    private String modelRequestStatus = "NONE";

    @Column(name = "model_request_photos", columnDefinition = "TEXT")
    private String modelRequestPhotos;

    @Column(name = "model_request_notes", columnDefinition = "TEXT")
    private String modelRequestNotes;

    @Column(name = "model_requested_at")
    private Instant modelRequestedAt;

    @Column(name = "model_completed_at")
    private Instant modelCompletedAt;

    // ---- v5: dish-level analytics ----

    @Builder.Default
    @Column(name = "views_count", nullable = false)
    private Long viewsCount = 0L;

    @Builder.Default
    @Column(name = "model_views_count", nullable = false)
    private Long modelViewsCount = 0L;

    @Builder.Default
    @Column(name = "ar_launches_count", nullable = false)
    private Long arLaunchesCount = 0L;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (uploadStatus == null) {
            uploadStatus = UploadStatus.PRESIGNED;
        }
        if (dishCode == null || dishCode.isBlank()) {
            dishCode = "DSH-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }
    }

    /** True when the primary file is a glTF/GLB (a v2 "3D-only" dish) rather than a photo. */
    public boolean isModel() {
        return contentType != null && contentType.startsWith("model/");
    }

    public void clearPendingImage() {
        pendingImageS3Key = null;
        pendingImageSizeBytes = null;
        pendingImageOriginalSizeBytes = null;
        pendingImageContentType = null;
        pendingImageCreatedAt = null;
    }

    /** True when a 3D model can be shown for this dish. */
    public boolean hasModel() {
        return modelS3Key != null && !modelS3Key.isBlank();
    }

    /** True for v2 rows whose only file is the .glb (no photo). */
    public boolean isLegacyModelOnly() {
        return isModel();
    }

    /** Bytes this dish occupies in S3 once completed (a legacy model-only file is counted once). */
    public long storageBytes() {
        long photo = isModel() || fileSizeBytes == null ? 0L : fileSizeBytes;
        long model = modelFileSizeBytes == null ? 0L : modelFileSizeBytes;
        return photo + model;
    }
}
