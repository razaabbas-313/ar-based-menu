package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.Attach3dRequestDto;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.ModelConfirmDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.ReplaceAssetKind;
import com.atelier.media.dto.ReplaceConfirmDto;
import com.atelier.media.dto.ReplacePresignDto;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.UploadVerificationException;
import com.atelier.media.model.Media;
import com.atelier.media.model.MediaType;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;

/**
 * In-place replacement of a dish's photo or 3D model, without deleting the dish.
 * <ol>
 *   <li>replace-presign: tenant check, then a SigV4 PUT URL for a NEW key
 *       ({@code restaurant-atelier/{slug}/{segment}/{uuid}.{ext}}). The new key is parked in the
 *       {@code pending_*} columns; the live asset keeps serving guests.</li>
 *   <li>Browser streams the bytes straight to S3.</li>
 *   <li>replace-confirm: S3 HEAD (size + ETag) with no DB transaction open, then one short
 *       transaction swaps the keys and adjusts the storage counter, then the OLD object is deleted
 *       after commit and only this restaurant's menu cache entry is evicted.</li>
 * </ol>
 * Models reuse the attach-3d track of {@link DishService}, which follows the same rules.
 */
@Slf4j
@Service
public class MediaReplacementService {

    private final MediaRepository mediaRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantAccess restaurantAccess;
    private final S3Storage storage;
    private final AtelierProperties props;
    private final TransactionTemplate tx;
    private final MenuCache menuCache;
    private final DishService dishService;

    public MediaReplacementService(MediaRepository mediaRepository,
                                   RestaurantRepository restaurantRepository,
                                   RestaurantAccess restaurantAccess,
                                   S3Storage storage,
                                   AtelierProperties props,
                                   TransactionTemplate tx,
                                   MenuCache menuCache,
                                   DishService dishService) {
        this.mediaRepository = mediaRepository;
        this.restaurantRepository = restaurantRepository;
        this.restaurantAccess = restaurantAccess;
        this.storage = storage;
        this.props = props;
        this.tx = tx;
        this.menuCache = menuCache;
        this.dishService = dishService;
    }

    // ------------------------------------------------------------------ step 1

    public PresignResponseDto presign(Tenant tenant, long id, ReplacePresignDto request) {
        if (request.assetType() == ReplaceAssetKind.MODEL) {
            return dishService.requestModelUpload(tenant, id, new Attach3dRequestDto(
                    request.filename(), request.contentType(), request.uploadSizeBytes(), request.arPlacement()));
        }
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        storage.requireConfigured();
        String contentType = MediaRules.normalizeContentType(request.contentType());
        String extension = MediaRules.requireImage(contentType);
        MediaRules.requireCompatibleExtension(MediaRules.sanitizeFilename(request.filename()), contentType);
        MediaRules.requireMaxSize(request.uploadSizeBytes(), props.maxImageBytes(), "compressed photos");

        String[] previousPending = new String[1];
        String[] key = new String[1];
        Media dish = tx.execute(status -> {
            Media d = requireCompletedDish(tenant, id);
            MediaType photoType = d.getMediaType() == MediaType.DAILY_SPECIAL ? MediaType.DAILY_SPECIAL : MediaType.IMAGE_2D;
            // Slug comes from the database, never from the request or token.
            key[0] = storage.newKey(restaurant.getSlug(), photoType.keySegment(), "", extension);
            previousPending[0] = d.getPendingImageS3Key();
            d.setPendingImageS3Key(key[0]);
            d.setPendingImageSizeBytes(request.uploadSizeBytes());
            d.setPendingImageOriginalSizeBytes(request.originalSizeBytes());
            d.setPendingImageContentType(contentType);
            d.setPendingImageCreatedAt(Instant.now());
            return mediaRepository.save(d);
        });
        S3Storage.PresignedUpload upload = storage.presignPut(key[0], contentType, request.uploadSizeBytes());
        log.info("Presigned photo replacement restaurant={} dish={} key={} bytes={}",
                restaurant.getSlug(), id, key[0], request.uploadSizeBytes());
        return new PresignResponseDto(upload.url(), key[0], storage.publicUrl(key[0]), dish.getId(),
                upload.expiresAt(), upload.expiresInSeconds(), upload.requiredHeaders());
    }

    // ------------------------------------------------------------------ step 3

    public MediaResponseDto confirm(Tenant tenant, long id, ReplaceConfirmDto confirm) {
        if (confirm.assetType() == ReplaceAssetKind.MODEL) {
            return dishService.confirmModelUpload(tenant, id, new ModelConfirmDto(confirm.s3Key(), confirm.etag()));
        }
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        Media dish = mediaRepository.findByIdAndRestaurantId(id, tenant.restaurantId()).orElseThrow(() -> notFound(id));
        if (confirm.s3Key().equals(dish.getS3Key())) {
            return DishMapper.toDto(dish); // already swapped (idempotent retry)
        }
        if (dish.getPendingImageS3Key() == null || !dish.getPendingImageS3Key().equals(confirm.s3Key())) {
            throw new UploadVerificationException("This photo upload is no longer pending. Please choose the photo again.");
        }

        // S3 HEAD with no transaction (and no pooled DB connection) held.
        HeadObjectResponse head = storage.head(dish.getS3Bucket(), confirm.s3Key());
        Long actualSize = head.contentLength();
        String s3Etag = MediaService.stripQuotes(head.eTag());
        String clientEtag = MediaService.stripQuotes(confirm.etag());
        if (actualSize == null || !actualSize.equals(dish.getPendingImageSizeBytes())) {
            discardPending(tenant, id, confirm.s3Key());
            throw new UploadVerificationException("Size mismatch: expected %s bytes but S3 holds %s bytes."
                    .formatted(dish.getPendingImageSizeBytes(), actualSize));
        }
        if (clientEtag != null && !clientEtag.isBlank() && s3Etag != null && !clientEtag.equals(s3Etag)) {
            discardPending(tenant, id, confirm.s3Key());
            throw new UploadVerificationException("ETag mismatch: the object in S3 is not the one this browser uploaded.");
        }

        String[] oldKey = new String[1];
        Media saved = tx.execute(status -> {
            Media d = mediaRepository.findByIdAndRestaurantId(id, tenant.restaurantId()).orElseThrow(() -> notFound(id));
            if (!confirm.s3Key().equals(d.getPendingImageS3Key())) {
                throw new UploadVerificationException("This photo upload is no longer pending. Please choose the photo again.");
            }
            long before = d.storageBytes();
            boolean wasLegacyModelOnly = d.isLegacyModelOnly();
            String previousKey = d.getS3Key();
            // A v2 "3D-only" dish keeps its .glb as the 3D model and gains a real photo.
            oldKey[0] = previousKey.equals(d.getModelS3Key()) ? null : previousKey;
            String contentType = d.getPendingImageContentType();

            d.setS3Key(confirm.s3Key());
            d.setS3Url(storage.publicUrl(confirm.s3Key()));
            d.setStoredFileName(confirm.s3Key().substring(confirm.s3Key().lastIndexOf('/') + 1));
            d.setContentType(contentType);
            d.setFileExtension(MediaRules.IMAGE_TYPES.get(contentType));
            d.setFileSizeBytes(actualSize);
            d.setOriginalSizeBytes(d.getPendingImageOriginalSizeBytes());
            d.setEtag(s3Etag != null ? s3Etag : clientEtag);
            d.setWidthPx(confirm.widthPx());
            d.setHeightPx(confirm.heightPx());
            if (wasLegacyModelOnly && d.getMediaType() == MediaType.MODEL_3D) {
                d.setMediaType(MediaType.IMAGE_2D);
            }
            d.clearPendingImage();
            Media updated = mediaRepository.save(d);
            restaurantRepository.adjustUsage(tenant.restaurantId(), updated.storageBytes() - before, 0, 0);
            return updated;
        });

        // Post-commit cleanup of the replaced object: the DB no longer references it.
        menuCache.evict(restaurant.getSlug());
        log.info("Photo replaced restaurant={} dish={} key={} bytes={} cleanupRequested={}",
                restaurant.getSlug(), id, confirm.s3Key(), actualSize, oldKey[0]);
        return DishMapper.toDto(saved);
    }

    // ------------------------------------------------------------------ helpers

    private Media requireCompletedDish(Tenant tenant, long id) {
        Media dish = mediaRepository.findByIdAndRestaurantId(id, tenant.restaurantId()).orElseThrow(() -> notFound(id));
        if (dish.getUploadStatus() != UploadStatus.COMPLETED) {
            throw notFound(id);
        }
        return dish;
    }

    private void discardPending(Tenant tenant, long id, String key) {
        tx.executeWithoutResult(status -> mediaRepository.findByIdAndRestaurantId(id, tenant.restaurantId()).ifPresent(d -> {
            if (key.equals(d.getPendingImageS3Key())) {
                d.clearPendingImage();
                mediaRepository.save(d);
            }
        }));
    }

    private static MediaNotFoundException notFound(long id) {
        return new MediaNotFoundException("Dish " + id + " not found");
    }
}
