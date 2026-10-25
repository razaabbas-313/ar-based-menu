package com.atelier.media.security;

import com.atelier.media.exception.InvalidTokenException;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated super admin behind an /api/v1/admin request (from a verified JWT only). */
public record AdminPrincipal(long adminId, String email, Integer tokenVersion) {

    public static final String CLAIM_ADMIN_ID = "aid";

    public AdminPrincipal(long adminId, String email) {
        this(adminId, email, 1);
    }

    public static AdminPrincipal from(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidTokenException("Sign in as the platform owner to continue.");
        }
        Object aid = jwt.getClaim(CLAIM_ADMIN_ID);
        String email = jwt.getClaimAsString("email");
        if (!(aid instanceof Number number) || email == null) {
            throw new InvalidTokenException("This session is not a platform admin session. Please sign in again.");
        }
        Number ver = jwt.getClaim(TokenService.CLAIM_VERSION);
        Integer tokenVersion = ver != null ? ver.intValue() : 1;
        return new AdminPrincipal(number.longValue(), email, tokenVersion);
    }
}
