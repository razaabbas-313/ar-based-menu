package com.atelier.media.repository;

import com.atelier.media.model.ModelRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModelRequestRepository extends JpaRepository<ModelRequest, Long> {

    List<ModelRequest> findByRestaurantIdOrderByRequestedAtDesc(Long restaurantId);

    List<ModelRequest> findByDishIdOrderByRequestedAtDesc(Long dishId);

    Optional<ModelRequest> findFirstByDishIdAndStatusIn(Long dishId, Collection<String> statuses);

    Optional<ModelRequest> findFirstByDishIdAndStatusOrderByRequestedAtDesc(Long dishId, String status);

    List<ModelRequest> findByStatusOrderByRequestedAtDesc(String status);

    List<ModelRequest> findAllByOrderByRequestedAtDesc();

    @Query("""
            SELECT r FROM ModelRequest r
             WHERE (:restaurantId IS NULL OR r.restaurantId = :restaurantId)
               AND (:status IS NULL OR UPPER(r.status) = UPPER(:status))
             ORDER BY r.requestedAt DESC
            """)
    Page<ModelRequest> findFiltered(
            @Param("restaurantId") Long restaurantId,
            @Param("status") String status,
            Pageable pageable
    );
}
