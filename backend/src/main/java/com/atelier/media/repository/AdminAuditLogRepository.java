package com.atelier.media.repository;

import com.atelier.media.model.AdminAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);
}
