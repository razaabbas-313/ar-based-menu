package com.atelier.media.repository;

import com.atelier.media.model.DishCategory;
import com.atelier.media.model.Media;
import com.atelier.media.model.MediaType;
import com.atelier.media.model.UploadStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tenant rule: every request-path lookup goes through a {@code ...AndRestaurantId} method.
 * The unscoped finders exist for internal jobs (reaper, admin tooling) only.
 */
@Repository
public interface MediaRepository extends JpaRepository<Media, Long> {

    // ---- tenant-scoped (use these from request handlers) ----

    Optional<Media> findByIdAndRestaurantId(Long id, Long restaurantId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.id = :id AND m.restaurantId = :restaurantId")
    Optional<Media> findByIdAndRestaurantIdForUpdate(@Param("id") Long id, @Param("restaurantId") Long restaurantId);

    Optional<Media> findByS3KeyAndRestaurantId(String s3Key, Long restaurantId);

    List<Media> findByRestaurantIdAndUploadStatusOrderByCreatedAtDesc(Long restaurantId, UploadStatus status, Pageable pageable);

    /** Guest menu: completed and in stock. Backed by idx_media_details_public_menu. */
    List<Media> findByRestaurantIdAndUploadStatusAndAvailableTrueOrderByCreatedAtDesc(Long restaurantId, UploadStatus status, Pageable pageable);

    List<Media> findByRestaurantIdAndMediaTypeAndUploadStatus(Long restaurantId, MediaType type, UploadStatus status);

    List<Media> findByRestaurantIdAndDishCategoryAndUploadStatus(Long restaurantId, DishCategory category, UploadStatus status);

    List<Media> findByRestaurantId(Long restaurantId);

    long countByRestaurantIdAndUploadStatus(Long restaurantId, UploadStatus status);

    long countByRestaurantId(Long restaurantId);

    boolean existsByRestaurantIdAndDishCode(Long restaurantId, String dishCode);

    Optional<Media> findByRestaurantIdAndDishCode(Long restaurantId, String dishCode);

    long countByRestaurantIdAndUploadStatusAndModelS3KeyIsNotNull(Long restaurantId, UploadStatus status);

    @Query("""
            select coalesce(sum(case when m.contentType like 'model/%' then 0 else m.fileSizeBytes end + m.modelFileSizeBytes), 0)
              from Media m
             where m.restaurantId = :restaurantId and m.uploadStatus = :status
            """)
    Number sumStorageBytes(@Param("restaurantId") Long restaurantId, @Param("status") UploadStatus status);

    // ---- unscoped (internal only; never call with request input) ----

    Optional<Media> findByS3Key(String s3Key);

    List<Media> findByUploadStatusOrderByCreatedAtDesc(UploadStatus status, Pageable pageable);

    List<Media> findByMediaTypeAndUploadStatus(MediaType type, UploadStatus status);

    List<Media> findByDishCategoryAndUploadStatus(DishCategory category, UploadStatus status);

    /** PRESIGNED rows whose token was issued before {@code cutoff} and never confirmed. */
    List<Media> findTop200ByUploadStatusAndCreatedAtBefore(UploadStatus status, Instant cutoff);

    /** Dishes with an attach-3d upload that was never confirmed. */
    List<Media> findTop200ByPendingModelS3KeyIsNotNullAndPendingModelCreatedAtBefore(Instant cutoff);

    /** Replacement photos that were presigned but never confirmed (reaper). */
    List<Media> findTop200ByPendingImageS3KeyIsNotNullAndPendingImageCreatedAtBefore(Instant cutoff);

    // ---- v5: 3D concierge & analytics ----

    List<Media> findByModelRequestStatusOrderByModelRequestedAtDesc(String modelRequestStatus, Pageable pageable);

    default List<Media> findByModelRequestStatusOrderByModelRequestedAtDesc(String modelRequestStatus) {
        return findByModelRequestStatusOrderByModelRequestedAtDesc(modelRequestStatus, org.springframework.data.domain.PageRequest.of(0, 200));
    }

    long countByModelRequestStatus(String modelRequestStatus);

    List<Media> findTop5ByRestaurantIdAndUploadStatusOrderByViewsCountDesc(Long restaurantId, UploadStatus status);

    @Query("select m from Media m where m.id>:after and m.modelRequestStatus='COMPLETED' and m.modelCompletedAt<:cutoff and m.modelRequestPhotos is not null and m.modelRequestPhotos<>'' and m.modelRequestPhotos<>'[]' order by m.id")
    List<Media> findExpiredReferenceCandidates(@Param("after") long after, @Param("cutoff") Instant cutoff, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("UPDATE Media m SET m.viewsCount = m.viewsCount + 1 WHERE m.id = :id")
    int incrementViews(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("UPDATE Media m SET m.modelViewsCount = m.modelViewsCount + 1 WHERE m.id = :id")
    int incrementModelViews(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("UPDATE Media m SET m.arLaunchesCount = m.arLaunchesCount + 1 WHERE m.id = :id")
    int incrementArLaunches(@Param("id") Long id);
}
