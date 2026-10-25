package com.atelier.media.security;

import com.atelier.media.exception.InvalidTokenException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated restaurant behind a staff request. Built only from a verified JWT, never
 * from request bodies or paths, so a client cannot choose which tenant it acts as.
 *
 * @param impersonated true for a super-admin support session ("open studio as owner")
 */
public record Tenant(long restaurantId, String slug, boolean impersonated, Integer tokenVersion) {

    public static final String CLAIM_RESTAURANT_ID = "rid";
    public static final String CLAIM_SLUG = "slug";

    public Tenant(long restaurantId, String slug) {
        this(restaurantId, slug, false, 1);
    }

    public Tenant(long restaurantId, String slug, boolean impersonated) {
        this(restaurantId, slug, impersonated, 1);
    }

    public static Tenant from(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidTokenException("Sign in to your restaurant studio to continue.");
        }
        Object rid = jwt.getClaim(CLAIM_RESTAURANT_ID);
        String slug = jwt.getClaimAsString(CLAIM_SLUG);
        long restaurantId;
        if (rid instanceof Number number) {
            restaurantId = number.longValue();
        } else if (rid instanceof String text && text.matches("\\d{1,18}")) {
            restaurantId = Long.parseLong(text);
        } else {
            throw new InvalidTokenException("This session is not linked to a restaurant. Please sign in again.");
        }
        if (slug == null || slug.isBlank()) {
            throw new InvalidTokenException("This session is not linked to a restaurant. Please sign in again.");
        }
        Number ver = jwt.getClaim(TokenService.CLAIM_VERSION);
        Integer tokenVersion = ver != null ? ver.intValue() : 1;
        return new Tenant(restaurantId, slug, jwt.getClaimAsString(TokenService.IMPERSONATED_BY_CLAIM) != null, tokenVersion);
    }
}
