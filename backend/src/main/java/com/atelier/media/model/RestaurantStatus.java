package com.atelier.media.model;

/**
 * Tenant lifecycle, controlled by the super admin.
 * <ul>
 *   <li>ACTIVE / TRIAL: studio and public menu work.</li>
 *   <li>PENDING: owner can prepare the studio; the public menu is hidden.</li>
 *   <li>SUSPENDED: sign-in, studio and public menu are blocked.</li>
 * </ul>
 */
public enum RestaurantStatus {
    ACTIVE,
    TRIAL,
    PENDING,
    PENDING_ONBOARDING,
    SUSPENDED;

    public boolean canUseStudio() {
        return this != SUSPENDED;
    }

    public boolean isPubliclyVisible() {
        return this == ACTIVE || this == TRIAL;
    }
}
