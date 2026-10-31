package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.PresignRequestDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.UploadConfirmDto;
import com.atelier.media.exception.InvalidMediaException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.UploadVerificationException;
import com.atelier.media.model.DishCategory;
import com.atelier.media.model.Media;
import com.atelier.media.model.MediaType;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.CategoryRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Zero-server-RAM photo pipeline (new dish = photo first): sign a PUT URL, verify the upload with
 * an S3 HEAD, publish the dish and update the restaurant's usage counters.
 * <p>
 * Tenancy: every operation takes a {@link Tenant} built from the verified JWT and only loads rows
 * through {@code ...AndRestaurantId} finders; another tenant's keys or ids are reported as "not found".
 * <p>
 * Connection hygiene: S3 network calls (HEAD, DELETE) never run inside a DB transaction.
 */
@Slf4j
@Service
public class MediaService {
    private EntitlementService entitlements;
    @org.springframework.beans.factory.annotation.Autowired
    public void setEntitlements(EntitlementService entitlements) { this.entitlements=entitlements; }


    private final MediaRepository mediaRepository;
    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantAccess restaurantAccess;
    private final S3Storage storage;
    private final AtelierProperties props;
    private final TransactionTemplate tx;
    private final MenuCache menuCache;

    public MediaService(MediaRepository mediaRepository, CategoryRepository categoryRepository,
                        RestaurantRepository restaurantRepository,
                        RestaurantAccess restaurantAccess,
                        S3Storage storage,
                        AtelierProperties props,
                        TransactionTemplate tx,
                        MenuCache menuCache) {
        this.mediaRepository = mediaRepository;
        this.categoryRepository = categoryRepository;
        this.restaurantRepository = restaurantRepository;
        this.restaurantAccess = restaurantAccess;
        this.storage = storage;
        this.props = props;
        this.tx = tx;
        this.menuCache = menuCache;
    }

    // ------------------------------------------------------------------ presign

    public PresignResponseDto generatePresignedUploadUrl(Tenant tenant, PresignRequestDto request) {
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        if (request.categoryId() != null) {
            categoryRepository.findByIdAndRestaurantId(request.categoryId(), tenant.restaurantId())
                    .orElseThrow(() -> new InvalidFieldException("categoryId", "Category not found."));
        }
        storage.requireConfigured();

        String contentType = MediaRules.normalizeContentType(request.contentType());
        String extension = MediaRules.requireImage(contentType);
        if (request.mediaType() == MediaType.MODEL_3D) {
            throw new InvalidMediaException("New dishes start from a photo (IMAGE_2D or DAILY_SPECIAL). Attach the 3D model afterwards.");
        }
        String displayName = MediaRules.sanitizeFilename(request.filename());
        MediaRules.requireCompatibleExtension(displayName, contentType);
        MediaRules.requireMaxSize(request.uploadSizeBytes(), props.maxImageBytes(), "compressed photos");
        String tags = DietaryTags.toColumn(request.dietaryTags());

        // Slug comes from the database, never from the token.
        String s3Key = storage.newKey(restaurant.getSlug(), request.mediaType().keySegment(), "", extension);

        Media media = Media.builder()
                .restaurantId(restaurant.getId())
                .originalFileName(displayName)
                .storedFileName(s3Key.substring(s3Key.lastIndexOf('/') + 1))
                .s3Bucket(storage.bucket())
                .s3Key(s3Key)
                .s3Url(storage.publicUrl(s3Key))
                .contentType(contentType)
                .fileExtension(extension)
                .fileSizeBytes(request.uploadSizeBytes())
                .originalSizeBytes(request.originalSizeBytes())
                .mediaType(request.mediaType())
                .uploadStatus(UploadStatus.PRESIGNED)
                .dishTitle(request.dishTitle().strip())
                .priceTag(request.priceTag())
                .dishCategory(request.dishCategory() != null ? request.dishCategory() : DishCategory.MAINS)
                .categoryId(request.categoryId())
                .hasArSupport(false)
                .arPlacement(Media.PLACEMENT_FLOOR.equals(request.arPlacement()) ? Media.PLACEMENT_FLOOR : Media.PLACEMENT_TABLE)
                .description(blankToNull(request.description()))
                .dietaryTags(tags)
                .available(request.available() == null || request.available())
                .uploadedByUserId("restaurant:" + restaurant.getId())
                .build();

        Media saved = tx.execute(status -> {
            Restaurant locked = restaurantRepository.findByIdForUpdate(restaurant.getId())
                    .orElseThrow(() -> new com.atelier.media.exception.RestaurantNotFoundException(restaurant.getId()));
            restaurantAccess.requireStudioAccess(tenant);
            if(entitlements!=null)entitlements.requireDishCapacity(tenant.restaurantId(),false);
            long seq = locked.getNextDishSeq() == null ? 1L : locked.getNextDishSeq();
            locked.setNextDishSeq(seq + 1);
            restaurantRepository.save(locked);
            media.setDishCode(String.format("DSH-%04d", seq));
            return mediaRepository.save(media);
        });

        S3Storage.PresignedUpload upload = storage.presignPut(s3Key, contentType, request.uploadSizeBytes());
        log.info("Presigned photo restaurant={} id={} key={} bytes={}", restaurant.getSlug(), saved.getId(), s3Key, request.uploadSizeBytes());
        return new PresignResponseDto(upload.url(), s3Key, saved.getS3Url(), saved.getId(),
                upload.expiresAt(), upload.expiresInSeconds(), upload.requiredHeaders());
    }

    // ------------------------------------------------------------------ confirm

    /** Not transactional: the S3 HEAD runs with no DB connection held. */
    public MediaResponseDto confirmUpload(Tenant tenant, UploadConfirmDto confirm) {
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        Media media = mediaRepository.findByS3KeyAndRestaurantId(confirm.s3Key(), tenant.restaurantId())
                .orElseThrow(() -> new MediaNotFoundException("No upload registered for key " + confirm.s3Key()));

        if (media.getUploadStatus() == UploadStatus.COMPLETED) {
            return DishMapper.toDto(media);
        }
        if (media.getUploadStatus() == UploadStatus.FAILED) {
            throw new UploadVerificationException("This upload token has expired. Please upload the file again.");
        }

        HeadObjectResponse head = storage.head(media.getS3Bucket(), media.getS3Key());
        Long actualSize = head.contentLength();
        if (actualSize == null || !actualSize.equals(media.getFileSizeBytes())) {
            markFailed(media.getId());
            throw new UploadVerificationException("Size mismatch: expected %d bytes but S3 holds %s bytes."
                    .formatted(media.getFileSizeBytes(), actualSize));
        }
        String etag = stripQuotes(head.eTag() != null ? head.eTag() : confirm.etag());
        long mediaId = media.getId();

        Media saved = tx.execute(status -> {
            restaurantRepository.findByIdForUpdate(tenant.restaurantId()).orElseThrow();
            restaurantAccess.requireStudioAccess(tenant);
            Media fresh = mediaRepository.findByIdAndRestaurantId(mediaId, tenant.restaurantId())
                    .orElseThrow(() -> new MediaNotFoundException("This upload was removed."));
            if (fresh.getUploadStatus() == UploadStatus.COMPLETED) {
                return fresh;
            }
            if (fresh.getUploadStatus() == UploadStatus.FAILED) {
                throw new UploadVerificationException("This upload token has expired. Please upload the file again.");
            }
            if(entitlements!=null)entitlements.requireDishCapacity(tenant.restaurantId(),true);
            fresh.setUploadStatus(UploadStatus.COMPLETED);
            fresh.setEtag(etag);
            fresh.setFileSizeBytes(actualSize);
            fresh.setWidthPx(confirm.widthPx());
            fresh.setHeightPx(confirm.heightPx());
            fresh.setCompletedAt(Instant.now());
            Media completed = mediaRepository.save(fresh);
            restaurantRepository.adjustUsage(tenant.restaurantId(), completed.storageBytes(), 1, completed.hasModel() ? 1 : 0);
            return completed;
        });

        menuCache.evict(restaurant.getSlug());
        log.info("Photo confirmed restaurant={} id={} key={} bytes={}", restaurant.getSlug(), mediaId, media.getS3Key(), actualSize);
        return DishMapper.toDto(saved);
    }

    // ------------------------------------------------------------------ housekeeping

    private record StoredObject(String bucket, String key) {
    }

    /**
     * Called by {@link StaleUploadReaper}. Stale rows / pending 3D attachments / pending replacement photos are updated in short
     * transactions that commit immediately; orphaned S3 objects are deleted afterwards, with the
     * connection already back in the pool.
     */
    public int expireStalePresignedUploads() {
        Instant cutoff = Instant.now().minus(props.staleUploadTtl());
        List<StoredObject> orphans = new ArrayList<>();
        try {
            List<StoredObject> photos = tx.execute(status -> {
                List<Media> stale = mediaRepository.findTop200ByUploadStatusAndCreatedAtBefore(UploadStatus.PRESIGNED, cutoff);
                stale.forEach(m -> m.setUploadStatus(UploadStatus.FAILED));
                mediaRepository.saveAll(stale);
                return stale.stream().map(m -> new StoredObject(m.getS3Bucket(), m.getS3Key())).toList();
            });
            if (photos != null) {
                orphans.addAll(photos);
            }
            List<StoredObject> models = tx.execute(status -> {
                List<Media> stale = mediaRepository.findTop200ByPendingModelS3KeyIsNotNullAndPendingModelCreatedAtBefore(cutoff);
                List<StoredObject> keys = stale.stream().map(m -> new StoredObject(m.getS3Bucket(), m.getPendingModelS3Key())).toList();
                stale.forEach(m -> {
                    m.setPendingModelS3Key(null);
                    m.setPendingModelSizeBytes(null);
                    m.setPendingModelContentType(null);
                    m.setPendingModelCreatedAt(null);
                });
                mediaRepository.saveAll(stale);
                return keys;
            });
            if (models != null) {
                orphans.addAll(models);
            }
            List<StoredObject> photos2 = tx.execute(status -> {
                List<Media> stale = mediaRepository.findTop200ByPendingImageS3KeyIsNotNullAndPendingImageCreatedAtBefore(cutoff);
                List<StoredObject> keys = stale.stream().map(m -> new StoredObject(m.getS3Bucket(), m.getPendingImageS3Key())).toList();
                stale.forEach(Media::clearPendingImage);
                mediaRepository.saveAll(stale);
                return keys;
            });
            if (photos2 != null) {
                orphans.addAll(photos2);
            }
        } catch (OptimisticLockingFailureException e) {
            log.info("Stale-upload sweep skipped this round: {}", e.getMessage());
        }
        return orphans.size();
    }

    // ------------------------------------------------------------------ helpers

    private void markFailed(long mediaId) {
        tx.executeWithoutResult(status -> mediaRepository.findById(mediaId).ifPresent(m -> {
            if (m.getUploadStatus() == UploadStatus.PRESIGNED) {
                m.setUploadStatus(UploadStatus.FAILED);
                mediaRepository.save(m);
            }
        }));
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static String stripQuotes(String value) {
        return value == null ? null : value.replace("\"", "");
    }
}
