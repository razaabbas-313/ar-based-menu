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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Historical record of a 3D digital twin concierge request, submission,
 * and resulting published model.
 */
@Entity
@Table(name = "model_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_IN_PRODUCTION = "IN_PRODUCTION";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String TYPE_NEW = "NEW";
    public static final String TYPE_REPLACEMENT = "REPLACEMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "dish_id", nullable = false)
    private Long dishId;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_PENDING;

    @Builder.Default
    @Column(name = "request_type", nullable = false, length = 30)
    private String requestType = TYPE_NEW;

    @Column(name = "reference_photos", columnDefinition = "TEXT")
    private String referencePhotos;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "completed_model_key", length = 512)
    private String completedModelKey;

    @Column(name = "completed_model_url", length = 1024)
    private String completedModelUrl;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
