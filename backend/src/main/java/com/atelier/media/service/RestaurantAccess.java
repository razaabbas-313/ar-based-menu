package com.atelier.media.service;

import com.atelier.media.exception.AccountSuspendedException;
import com.atelier.media.exception.InvalidTokenException;
import com.atelier.media.exception.PasswordChangeRequiredException;
import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Re-checks the tenant on every studio request, comparing token_version (cached for 30s)
 * and status, so suspending, deleting, or revoking a session takes effect promptly.
 */
@Component
public class RestaurantAccess {

    private final RestaurantRepository restaurantRepository;
    private final Cache<Long, Integer> tokenVersionCache;

    public RestaurantAccess(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
        this.tokenVersionCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(50_000)
                .build();
    }

    /**
     * Studio operations. Also blocks owners who still have a temporary password: a token issued
     * before the super admin reset the password cannot be used to keep working in the studio.
     */
    public Restaurant requireStudioAccess(Tenant tenant) {
        Restaurant restaurant = requireAccount(tenant);
        if (!tenant.impersonated() && restaurant.requiresPasswordChange()) {
            throw new PasswordChangeRequiredException();
        }
        return restaurant;
    }

    /** Account-level operations that stay available during the forced password change (me, change-password). */
    public Restaurant requireAccount(Tenant tenant) {
        // Validate token version if present (cached in Caffeine with 30s TTL)
        if (tenant.tokenVersion() != null) {
            Integer currentVersion = tokenVersionCache.get(tenant.restaurantId(), id ->
                    restaurantRepository.findById(id).map(Restaurant::getTokenVersion).orElse(null)
            );
            if (currentVersion == null || !currentVersion.equals(tenant.tokenVersion())) {
                throw new InvalidTokenException("This session has been revoked. Please sign in again.");
            }
        }

        Restaurant restaurant = restaurantRepository.findById(tenant.restaurantId())
                .orElseThrow(() -> new InvalidTokenException("This restaurant account no longer exists. Please sign in again."));
        if (restaurant.isDeleted()) {
            throw new InvalidTokenException("This restaurant account no longer exists. Please sign in again.");
        }
        if (!restaurant.getStatus().canUseStudio()) {
            throw new AccountSuspendedException();
        }
        return restaurant;
    }

    public void invalidate(Long restaurantId) {
        tokenVersionCache.invalidate(restaurantId);
    }
}
