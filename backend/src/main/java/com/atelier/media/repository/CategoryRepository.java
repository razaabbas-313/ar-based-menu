package com.atelier.media.repository;

import com.atelier.media.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByRestaurantIdOrderByDisplayOrderAscIdAsc(Long restaurantId);

    List<Category> findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(Long restaurantId);

    Optional<Category> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    long countByRestaurantId(Long restaurantId);
}
