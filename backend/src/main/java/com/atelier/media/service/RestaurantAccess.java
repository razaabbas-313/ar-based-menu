package com.atelier.media.service;

import com.atelier.media.exception.AccountSuspendedException;
import com.atelier.media.exception.InvalidTokenException;
import com.atelier.media.exception.PasswordChangeRequiredException;
import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import org.springframework.stereotype.Component;

/**
 * Re-checks the current tenant, token version and status on every protected operation.
 */
@Component
public class RestaurantAccess {

    private final RestaurantRepository restaurantRepository;

    public RestaurantAccess(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
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
        Restaurant restaurant = restaurantRepository.findById(tenant.restaurantId())
                .orElseThrow(() -> new InvalidTokenException("This restaurant account no longer exists. Please sign in again."));
        if (tenant.tokenVersion() == null || restaurant.getTokenVersion() != tenant.tokenVersion()) {
            throw new InvalidTokenException("This session has been revoked. Please sign in again.");
        }
        if (restaurant.isDeleted()) {
            throw new InvalidTokenException("This restaurant account no longer exists. Please sign in again.");
        }
        if (!restaurant.getStatus().canUseStudio()) {
            throw new AccountSuspendedException();
        }
        return restaurant;
    }

}
