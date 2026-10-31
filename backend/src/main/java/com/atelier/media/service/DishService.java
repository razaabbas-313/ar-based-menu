package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.Attach3dRequestDto;
import com.atelier.media.dto.DishUpdateDto;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.ModelConfirmDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.exception.ConflictException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.UploadVerificationException;
import com.atelier.media.model.Media;
import com.atelier.media.model.MediaType;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.repository.CategoryRepository;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Restaurant studio: dish CRUD and the separate 3D track (attach / replace / remove a .glb on an
 * existing dish without touching its photo). Counters are adjusted in the same short transaction as
 * the row change; S3 HEAD/DELETE calls happen outside any transaction.
 */
@Slf4j
@Service
public class DishService {

    private static final String LEGACY_3D_ONLY =
            "This dish was created as 3D-only in an earlier version and has no photo. Add a photo first (Update Visual Asset), then manage its 3D model.";

    private final MediaRepository mediaRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantAccess restaurantAccess;
    private final CategoryRepository categoryRepository;
    private final S3Storage storage;
    private final AtelierProperties props;
    private final TransactionTemplate tx;
    private final MenuCache menuCache;

    public DishService(MediaRepository mediaRepository,
                       RestaurantRepository restaurantRepository,
                       RestaurantAccess restaurantAccess,
                       S3Storage storage,
                       AtelierProperties props,
                       TransactionTemplate tx,
                       MenuCache menuCache) {
        this(mediaRepository, restaurantRepository, restaurantAccess, null, storage, props, tx, menuCache);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DishService(MediaRepository mediaRepository,
                       RestaurantRepository restaurantRepository,
                       RestaurantAccess restaurantAccess,
                       CategoryRepository categoryRepository,
                       S3Storage storage,
                       AtelierProperties props,
                       TransactionTemplate tx,
                       MenuCache menuCache) {
        this.mediaRepository = mediaRepository;
        this.restaurantRepository = restaurantRepository;
        this.restaurantAccess = restaurantAccess;
        this.categoryRepository = categoryRepository;
        this.storage = storage;
        this.props = props;
        this.tx = tx;
        this.menuCache = menuCache;
    }

    // ------------------------------------------------------------------ read

    public List<MediaResponseDto> listDishes(Tenant tenant) {
        restaurantAccess.requireStudioAccess(tenant);
        final java.util.Map<Long, String> catMap = categoryRepository != null
                ? categoryRepository.findByRestaurantIdOrderByDisplayOrderAscIdAsc(tenant.restaurantId())
                        .stream().collect(java.util.stream.Collectors.toMap(com.atelier.media.model.Category::getId, com.atelier.media.model.Category::getName, (a, b) -> a))
                : java.util.Collections.emptyMap();

        return mediaRepository.findByRestaurantIdAndUploadStatusOrderByCreatedAtDesc(
                        tenant.restaurantId(), UploadStatus.COMPLETED, PageRequest.of(0, props.menuLimit()))
                .stream().map(m -> DishMapper.toDto(m, m.getCategoryId() != null ? catMap.get(m.getCategoryId()) : null)).toList();
    }

    // ------------------------------------------------------------------ update

    public MediaResponseDto updateDish(Tenant tenant, long id, DishUpdateDto update) {
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        if (update.categoryId() != null) {
            categoryRepository.findByIdAndRestaurantId(update.categoryId(), tenant.restaurantId())
                    .orElseThrow(() -> new InvalidFieldException("categoryId", "Category not found."));
        }
        String tags = DietaryTags.toColumn(update.dietaryTags());
        Media saved = tx.execute(status -> {
            Media dish = requireCompletedDish(tenant, id);
            dish.setDishTitle(update.dishTitle().strip());
            dish.setPriceTag(update.priceTag());
            if (update.dishCategory() != null) {
                dish.setDishCategory(update.dishCategory());
            }
            if (update.categoryId() != null) {
                dish.setCategoryId(update.categoryId());
            }
            dish.setDescription(MediaService.blankToNull(update.description()));
            dish.setDietaryTags(tags);
            dish.setAvailable(update.available());
            if (update.arPlacement() != null) {
                dish.setArPlacement(update.arPlacement());
            }
            if (update.dailySpecial() != null) {
                dish.setMediaType(update.dailySpecial()
                        ? MediaType.DAILY_SPECIAL
                        : (dish.isLegacyModelOnly() ? MediaType.MODEL_3D : MediaType.IMAGE_2D));
            }
            return mediaRepository.save(dish);
        });
        menuCache.evict(restaurant.getSlug());
        String catName = saved.getCategoryId() != null ? categoryRepository.findByIdAndRestaurantId(saved.getCategoryId(), tenant.restaurantId()).map(com.atelier.media.model.Category::getName).orElse(null) : null;
        return DishMapper.toDto(saved, catName);
    }

    public MediaResponseDto setAvailability(Tenant tenant, long id, boolean available) {
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        Media saved = tx.execute(status -> {
            Media dish = requireCompletedDish(tenant, id);
            dish.setAvailable(available);
            return mediaRepository.save(dish);
        });
        menuCache.evict(restaurant.getSlug());
        return DishMapper.toDto(saved);
    }

    // ------------------------------------------------------------------ delete

    /** Row + counters in one short transaction, then every S3 file of the dish with no transaction open. */
    public void deleteDish(Tenant tenant, long id) {
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        Media removed = tx.execute(status -> {
            Media dish = mediaRepository.findByIdAndRestaurantId(id, tenant.restaurantId())
                    .orElseThrow(() -> notFound(id));
            mediaRepository.delete(dish);
            if (dish.getUploadStatus() == UploadStatus.COMPLETED) {
                restaurantRepository.adjustUsage(tenant.restaurantId(), -dish.storageBytes(), -1, dish.hasModel() ? -1 : 0);
            }
            return dish;
        });
        Set<String> keys = new LinkedHashSet<>();
        keys.add(removed.getS3Key());
        if (removed.getModelS3Key() != null) keys.add(removed.getModelS3Key());
        if (removed.getPendingModelS3Key() != null) keys.add(removed.getPendingModelS3Key());
        if (removed.getPendingImageS3Key() != null) keys.add(removed.getPendingImageS3Key());
        menuCache.evict(restaurant.getSlug());
        log.info("Deleted dish record restaurant={} id={} fileCleanupRequested={}", restaurant.getSlug(), id, keys.size());
    }

    // ------------------------------------------------------------------ 3D track

    /** Step 1 of attach/replace: presigned PUT for a .glb linked to this dish (pending until confirmed). */
    public PresignResponseDto requestModelUpload(Tenant tenant, long id, Attach3dRequestDto request) {
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        if (!Boolean.TRUE.equals(restaurant.getCanUpload3d())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Direct 3D model uploads are not enabled for this restaurant. Please request a 3D model through the 3D Concierge.");
        }
        storage.requireConfigured();
        String contentType = MediaRules.normalizeContentType(request.contentType());
        String extension = MediaRules.requireModel(contentType);
        MediaRules.requireCompatibleExtension(MediaRules.sanitizeFilename(request.filename()), contentType);
        MediaRules.requireMaxSize(request.uploadSizeBytes(), props.maxModelBytes(), "3D models");

        String key = storage.newKey(restaurant.getSlug(), MediaType.MODEL_3D.keySegment(), "", extension);
        String[] previousPending = new String[1];
        Media dish = tx.execute(status -> {
            Media d = requireCompletedDish(tenant, id);
            if (d.isLegacyModelOnly()) {
                throw new ConflictException("model", LEGACY_3D_ONLY);
            }
            previousPending[0] = d.getPendingModelS3Key();
            d.setPendingModelS3Key(key);
            d.setPendingModelSizeBytes(request.uploadSizeBytes());
            d.setPendingModelContentType(contentType);
            d.setPendingModelCreatedAt(Instant.now());
            if (request.arPlacement() != null) {
                d.setArPlacement(request.arPlacement());
            }
            return mediaRepository.save(d);
        });
        S3Storage.PresignedUpload upload = storage.presignPut(key, contentType, request.uploadSizeBytes());
        log.info("Presigned 3D attach restaurant={} dish={} key={} bytes={}", restaurant.getSlug(), id, key, request.uploadSizeBytes());
        return new PresignResponseDto(upload.url(), key, storage.publicUrl(key), dish.getId(),
                upload.expiresAt(), upload.expiresInSeconds(), upload.requiredHeaders());
    }

    /** Step 2: S3 HEAD outside any transaction, then swap the model in and adjust counters atomically. */
    public MediaResponseDto confirmModelUpload(Tenant tenant, long id, ModelConfirmDto confirm) {
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        if (!Boolean.TRUE.equals(restaurant.getCanUpload3d())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Direct 3D model uploads are not enabled for this restaurant.");
        }
        Media dish = mediaRepository.findByIdAndRestaurantId(id, tenant.restaurantId()).orElseThrow(() -> notFound(id));
        if (confirm.s3Key().equals(dish.getModelS3Key())) {
            return DishMapper.toDto(dish); // already confirmed (idempotent retry)
        }
        if (dish.getPendingModelS3Key() == null || !dish.getPendingModelS3Key().equals(confirm.s3Key())) {
            throw new UploadVerificationException("This 3D upload is no longer pending. Please attach the model again.");
        }

        HeadObjectResponse head = storage.head(dish.getS3Bucket(), confirm.s3Key());
        Long actualSize = head.contentLength();
        if (actualSize == null || !actualSize.equals(dish.getPendingModelSizeBytes())) {
            clearPending(tenant, id, confirm.s3Key());
            throw new UploadVerificationException("Size mismatch: expected %s bytes but S3 holds %s bytes."
                    .formatted(dish.getPendingModelSizeBytes(), actualSize));
        }

        String[] replacedKey = new String[1];
        Media saved = tx.execute(status -> {
            Media d = mediaRepository.findByIdAndRestaurantId(id, tenant.restaurantId()).orElseThrow(() -> notFound(id));
            if (!confirm.s3Key().equals(d.getPendingModelS3Key())) {
                throw new UploadVerificationException("This 3D upload is no longer pending. Please attach the model again.");
            }
            boolean hadModel = d.hasModel();
            long oldSize = d.getModelFileSizeBytes() == null ? 0 : d.getModelFileSizeBytes();
            replacedKey[0] = d.getModelS3Key();
            d.setModelS3Key(confirm.s3Key());
            d.setModelS3Url(storage.publicUrl(confirm.s3Key()));
            d.setModelFileSizeBytes(actualSize);
            d.setHasArSupport(true);
            d.setPendingModelS3Key(null);
            d.setPendingModelSizeBytes(null);
            d.setPendingModelContentType(null);
            d.setPendingModelCreatedAt(null);
            Media updated = mediaRepository.save(d);
            restaurantRepository.adjustUsage(tenant.restaurantId(), actualSize - oldSize, 0, hadModel ? 0 : 1);
            return updated;
        });
        menuCache.evict(restaurant.getSlug());
        log.info("3D model confirmed restaurant={} dish={} key={} bytes={} replaced={}",
                restaurant.getSlug(), id, confirm.s3Key(), actualSize, replacedKey[0]);
        return DishMapper.toDto(saved);
    }

    /** Detaches and deletes the dish's 3D model; the photo stays. */
    public MediaResponseDto removeModel(Tenant tenant, long id) {
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        String[] removedKey = new String[1];
        Media saved = tx.execute(status -> {
            Media d = requireCompletedDish(tenant, id);
            if (d.isLegacyModelOnly()) {
                throw new ConflictException("model", LEGACY_3D_ONLY);
            }
            if (!d.hasModel()) {
                return d;
            }
            long size = d.getModelFileSizeBytes() == null ? 0 : d.getModelFileSizeBytes();
            removedKey[0] = d.getModelS3Key();
            d.setModelS3Key(null);
            d.setModelS3Url(null);
            d.setModelFileSizeBytes(0L);
            d.setHasArSupport(false);
            Media updated = mediaRepository.save(d);
            restaurantRepository.adjustUsage(tenant.restaurantId(), -size, 0, -1);
            return updated;
        });
        if (removedKey[0] != null) {
            menuCache.evict(restaurant.getSlug());
        }
        return DishMapper.toDto(saved);
    }

    /**
     * Atomically allocates the next sequential dishCode for the restaurant using a row-level lock.
     * Guaranteed to produce strictly sequential, race-free dish codes (DSH-0001, DSH-0002, ...).
     */
    public String allocateNextDishCode(Long restaurantId) {
        return tx.execute(status -> {
            Restaurant restaurant = restaurantRepository.findByIdForUpdate(restaurantId)
                    .orElseThrow(() -> new com.atelier.media.exception.RestaurantNotFoundException(restaurantId));
            long seq = restaurant.getNextDishSeq() == null ? 1L : restaurant.getNextDishSeq();
            restaurant.setNextDishSeq(seq + 1);
            restaurantRepository.save(restaurant);
            return String.format("DSH-%04d", seq);
        });
    }

    // ------------------------------------------------------------------ helpers

    private Media requireCompletedDish(Tenant tenant, long id) {
        Media dish = mediaRepository.findByIdAndRestaurantId(id, tenant.restaurantId()).orElseThrow(() -> notFound(id));
        if (dish.getUploadStatus() != UploadStatus.COMPLETED) {
            throw notFound(id);
        }
        return dish;
    }

    private void clearPending(Tenant tenant, long id, String key) {
        tx.executeWithoutResult(status -> mediaRepository.findByIdAndRestaurantId(id, tenant.restaurantId()).ifPresent(d -> {
            if (key.equals(d.getPendingModelS3Key())) {
                d.setPendingModelS3Key(null);
                d.setPendingModelSizeBytes(null);
                d.setPendingModelContentType(null);
                d.setPendingModelCreatedAt(null);
                mediaRepository.save(d);
            }
        }));
    }

    private static MediaNotFoundException notFound(long id) {
        return new MediaNotFoundException("Dish " + id + " not found");
    }
}
