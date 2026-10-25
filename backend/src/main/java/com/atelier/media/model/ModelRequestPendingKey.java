package com.atelier.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "model_request_pending_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelRequestPendingKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "dish_id", nullable = false)
    private Long dishId;

    @Column(name = "s3_key", nullable = false, length = 512)
    private String s3Key;

    @Column(name = "slot", nullable = false, length = 32)
    private String slot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
