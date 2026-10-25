package com.atelier.media.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A tenant: one owner account, its branding, lifecycle status and usage counters. */
@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Public handle (/r/{slug}) and S3 namespace for new uploads. Editable by the super admin. */
    @Column(name = "slug", nullable = false, length = 60, unique = true)
    private String slug;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    /** Stored lower-cased. */
    @Column(name = "email", nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ---- lifecycle ----

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RestaurantStatus status = RestaurantStatus.ACTIVE;

    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 1;

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Soft delete marker. Soft-deleted tenants are hidden everywhere and cannot sign in. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ---- branding ----

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "logo_s3_key", length = 512)
    private String logoS3Key;

    @Builder.Default
    @Column(name = "logo_size_bytes", nullable = false)
    private Long logoSizeBytes = 0L;

    @Column(name = "banner_url", columnDefinition = "TEXT")
    private String bannerUrl;

    @Column(name = "banner_s3_key", length = 512)
    private String bannerS3Key;

    @Builder.Default
    @Column(name = "banner_size_bytes", nullable = false)
    private Long bannerSizeBytes = 0L;

    @Column(name = "tagline", length = 160)
    private String tagline;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "opening_hours", length = 500)
    private String openingHours;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "whatsapp_number", length = 50)
    private String whatsappNumber;

    @Column(name = "instagram_handle", length = 100)
    private String instagramHandle;

    @Column(name = "facebook_url", length = 255)
    private String facebookUrl;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @Builder.Default
    @Column(name = "theme_accent", nullable = false, length = 30)
    private String themeAccent = "amber";

    @Builder.Default
    @Column(name = "menu_layout", nullable = false, length = 20)
    private String menuLayout = "cards";

    @Builder.Default
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Builder.Default
    @Column(name = "currency_symbol", nullable = false, length = 10)
    private String currencySymbol = "$";

    // ---- usage counters (maintained atomically via RestaurantRepository.adjustUsage) ----

    @Builder.Default
    @Column(name = "storage_used_bytes", nullable = false)
    private Long storageUsedBytes = 0L;

    @Builder.Default
    @Column(name = "dish_count", nullable = false)
    private Integer dishCount = 0;

    @Builder.Default
    @Column(name = "next_dish_seq", nullable = false)
    private Long nextDishSeq = 1L;

    @Builder.Default
    @Column(name = "model_3d_count", nullable = false)
    private Integer model3dCount = 0;

    // ---- v4: provisioning & extra contact links ----

    /** True while the owner still signs in with the temporary password the super admin issued. */
    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = Boolean.FALSE;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "tiktok_handle", length = 100)
    private String tiktokHandle;

    @Column(name = "google_maps_url", length = 500)
    private String googleMapsUrl;

    // ---- v5: menu live master switch & super admin CRM notes ----

    @Builder.Default
    @Column(name = "is_menu_live", nullable = false)
    private Boolean isMenuLive = Boolean.TRUE;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    // ---- v6: 3d model direct upload permission ----

    @Builder.Default
    @Column(name = "can_upload_3d", nullable = false)
    private Boolean canUpload3d = Boolean.FALSE;

    // ---- v5: analytics counters ----

    @Builder.Default
    @Column(name = "menu_views_count", nullable = false)
    private Long menuViewsCount = 0L;

    @Builder.Default
    @Column(name = "model_views_count", nullable = false)
    private Long modelViewsCount = 0L;

    @Builder.Default
    @Column(name = "ar_launches_count", nullable = false)
    private Long arLaunchesCount = 0L;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = RestaurantStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean requiresPasswordChange() {
        return Boolean.TRUE.equals(mustChangePassword);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isPubliclyVisible() {
        return !isDeleted() && status != null && status.isPubliclyVisible();
    }
}
