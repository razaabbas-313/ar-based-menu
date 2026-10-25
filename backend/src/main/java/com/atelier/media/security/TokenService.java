package com.atelier.media.security;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.model.PlatformUser;
import com.atelier.media.model.Restaurant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Issues stateless HS256 session tokens. Authorization is carried in the {@code roles} claim:
 * {@code ROLE_RESTAURANT} for restaurant owners (and super-admin impersonation), {@code ROLE_RESTAURANT_SETUP}
 * for an owner who must still replace the temporary password, {@code ROLE_SUPER_ADMIN} for the platform owner.
 */
@Service
public class TokenService {

    public static final String ROLES_CLAIM = "roles";
    public static final String ROLE_RESTAURANT = "ROLE_RESTAURANT";
    public static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    /**
     * Granted instead of ROLE_RESTAURANT while the owner still uses a temporary password: the token
     * can only call /api/v1/auth/me and /api/v1/auth/change-password.
     */
    public static final String ROLE_RESTAURANT_SETUP = "ROLE_RESTAURANT_SETUP";
    /** Boolean claim mirrored to the client so it can show the "Set your new password" screen. */
    public static final String MUST_CHANGE_PASSWORD_CLAIM = "mustChangePassword";
    public static final String IMPERSONATED_BY_CLAIM = "imp";
    /** Kept for clients that read the v2 scope claim. */
    public static final String STUDIO_SCOPE = "studio";

    private final JwtEncoder encoder;
    private final AtelierProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, AtelierProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {
    }

    public IssuedToken issue(Restaurant restaurant) {
        return restaurantToken(restaurant, properties.jwt().ttl(), null);
    }

    /** Short-lived studio session opened by the super admin ("log in as owner"). */
    public IssuedToken issueImpersonation(Restaurant restaurant, String adminEmail) {
        return restaurantToken(restaurant, properties.jwt().impersonationTtl(), adminEmail);
    }

    public static final String AUDIENCE_STUDIO = "atelier-studio";
    public static final String AUDIENCE_ADMIN = "atelier-admin";
    public static final String CLAIM_VERSION = "ver";

    public IssuedToken issueAdmin(PlatformUser admin) {
        Instant now = clock.instant();
        Duration ttl = Duration.ofDays(30); // 30-day persistent admin session
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .audience(List.of(AUDIENCE_ADMIN))
                .subject("admin:" + admin.getId())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(AdminPrincipal.CLAIM_ADMIN_ID, admin.getId())
                .claim("email", admin.getEmail())
                .claim(CLAIM_VERSION, admin.getTokenVersion())
                .claim(ROLES_CLAIM, List.of(ROLE_SUPER_ADMIN))
                .build();
        return new IssuedToken(encode(claims), expiresAt, ttl.toSeconds());
    }

    private IssuedToken restaurantToken(Restaurant restaurant, Duration ttl, String impersonatedBy) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .audience(List.of(AUDIENCE_STUDIO))
                .subject(String.valueOf(restaurant.getId()))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(Tenant.CLAIM_RESTAURANT_ID, restaurant.getId())
                .claim(Tenant.CLAIM_SLUG, restaurant.getSlug())
                .claim("email", restaurant.getEmail())
                .claim(CLAIM_VERSION, restaurant.getTokenVersion())
                .claim("scope", STUDIO_SCOPE);
        // Impersonation (super-admin support sessions) is never blocked by the owner's password reset.
        boolean mustChange = impersonatedBy == null && restaurant.requiresPasswordChange();
        claims.claim(MUST_CHANGE_PASSWORD_CLAIM, mustChange)
                .claim(ROLES_CLAIM, List.of(mustChange ? ROLE_RESTAURANT_SETUP : ROLE_RESTAURANT));
        if (impersonatedBy != null) {
            claims.claim(IMPERSONATED_BY_CLAIM, impersonatedBy);
        }
        return new IssuedToken(encode(claims.build()), expiresAt, ttl.toSeconds());
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
