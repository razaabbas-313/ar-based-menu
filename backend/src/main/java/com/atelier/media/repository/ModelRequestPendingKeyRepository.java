package com.atelier.media.repository;

import com.atelier.media.model.ModelRequestPendingKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ModelRequestPendingKeyRepository extends JpaRepository<ModelRequestPendingKey, Long> {

    List<ModelRequestPendingKey> findByRestaurantIdAndDishId(Long restaurantId, Long dishId);

    void deleteByDishId(Long dishId);

    void deleteByCreatedAtBefore(Instant cutoff);
}
