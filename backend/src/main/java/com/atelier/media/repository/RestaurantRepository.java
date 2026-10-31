package com.atelier.media.repository;

import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, JpaSpecificationExecutor<Restaurant> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Restaurant r WHERE r.id = :id")
    Optional<Restaurant> findByIdForUpdate(@Param("id") Long id);

    Optional<Restaurant> findBySlug(String slug);

    Optional<Restaurant> findByEmail(String email);

    /** Serializes OTP issuance and redemption, including when no OTP row exists yet. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Restaurant r WHERE r.email = :email")
    Optional<Restaurant> findByEmailForUpdate(@Param("email") String email);

    boolean existsBySlug(String slug);

    boolean existsByEmail(String email);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    // ---- platform analytics ----

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(RestaurantStatus status);

    @Query("select coalesce(sum(r.storageUsedBytes), 0) from Restaurant r where r.deletedAt is null")
    Number totalStorageBytes();

    @Query("select coalesce(sum(r.dishCount), 0) from Restaurant r where r.deletedAt is null")
    Number totalDishes();

    @Query("select coalesce(sum(r.model3dCount), 0) from Restaurant r where r.deletedAt is null")
    Number totalModels();

    List<Restaurant> findTop5ByDeletedAtIsNullOrderByStorageUsedBytesDesc();

    List<Restaurant> findTop5ByDeletedAtIsNullOrderByDishCountDesc();

    /**
     * Atomic in-database counter update (no read-modify-write race between concurrent uploads).
     * Values are clamped at zero. Must run inside a transaction.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Restaurant r
               set r.storageUsedBytes = case when r.storageUsedBytes + :bytes < 0 then 0 else r.storageUsedBytes + :bytes end,
                   r.dishCount        = case when r.dishCount + :dishes < 0 then 0 else r.dishCount + :dishes end,
                   r.model3dCount     = case when r.model3dCount + :models < 0 then 0 else r.model3dCount + :models end
             where r.id = :id
            """)
    int adjustUsage(@Param("id") Long id,
                    @Param("bytes") long bytes,
                    @Param("dishes") int dishes,
                    @Param("models") int models);

    /** Overwrites the counters with freshly computed values (admin "recalculate usage"). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Restaurant r
               set r.storageUsedBytes = :bytes, r.dishCount = :dishes, r.model3dCount = :models
             where r.id = :id
            """)
    int setUsage(@Param("id") Long id,
                 @Param("bytes") long bytes,
                 @Param("dishes") int dishes,
                 @Param("models") int models);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Restaurant r SET r.menuViewsCount = r.menuViewsCount + 1 WHERE r.id = :id")
    int incrementMenuViews(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Restaurant r SET r.modelViewsCount = r.modelViewsCount + 1 WHERE r.id = :id")
    int incrementModelViews(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Restaurant r SET r.arLaunchesCount = r.arLaunchesCount + 1 WHERE r.id = :id")
    int incrementArLaunches(@Param("id") Long id);
}
