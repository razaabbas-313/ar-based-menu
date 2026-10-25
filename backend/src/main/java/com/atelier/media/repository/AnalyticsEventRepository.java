package com.atelier.media.repository;

import com.atelier.media.model.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    long countByRestaurantIdAndEventType(Long restaurantId, String eventType);

    long countByEventType(String eventType);

    @Query("SELECT e.dishId, COUNT(e) as cnt FROM AnalyticsEvent e WHERE e.restaurantId = :restaurantId AND e.dishId IS NOT NULL GROUP BY e.dishId ORDER BY cnt DESC")
    List<Object[]> findTopDishesByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(e) FROM AnalyticsEvent e WHERE e.restaurantId = :restaurantId AND e.createdAt >= :since")
    long countEventsSince(@Param("restaurantId") Long restaurantId, @Param("since") Instant since);
}
