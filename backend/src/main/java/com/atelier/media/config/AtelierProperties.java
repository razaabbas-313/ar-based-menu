package com.atelier.media.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/** Binds {@code atelier.*} from application.yml. */
@Validated
@ConfigurationProperties(prefix = "atelier")
public record AtelierProperties(
        /* Root folder inside the bucket; the tenant slug is appended. */
        @NotBlank String keyPrefix,
        /* Private folder inside the bucket for staff-only reference photos. */
        String privateKeyPrefix,
        /* Lifetime of a presigned PUT URL. */
        @NotNull Duration presignTtl,
        /* PRESIGNED rows / pending 3D attachments older than this are expired by the reaper. */
        @NotNull Duration staleUploadTtl,
        /* Upper bound for a compressed dish photo. */
        @Positive long maxImageBytes,
        /* Upper bound for a .glb/.gltf model. */
        @Positive long maxModelBytes,
        /* Upper bound for a published .glb model. */
        long maxPublishedModelBytes,
        /* Upper bound for a logo or banner image. */
        @Positive long maxBrandingBytes,
        /* Maximum number of dishes served by one restaurant's menu endpoint. */
        @Positive int menuLimit,
        /* Maximum number of restaurants listed in the public directory. */
        @Positive int directoryLimit,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Login login,
        @Valid @NotNull SuperAdmin superAdmin,
        @Valid @NotNull PasswordReset passwordReset,
        List<String> trustedProxies
) {

    @ConstructorBinding
    public AtelierProperties {
        if (privateKeyPrefix == null || privateKeyPrefix.isBlank()) {
            privateKeyPrefix = "restaurant-atelier-private";
        }
        if (maxPublishedModelBytes <= 0) {
            maxPublishedModelBytes = 25L * 1024 * 1024;
        }
        if (passwordReset == null) {
            passwordReset = new PasswordReset(false);
        }
        if (trustedProxies == null) {
            trustedProxies = List.of();
        }
    }

    public record PasswordReset(boolean enabled) {
    }

    /**
     * @param secret           HMAC-SHA256 key material, at least 32 bytes (ATELIER_JWT_SECRET)
     * @param issuer           value of the {@code iss} claim; tokens with another issuer are rejected
     * @param ttl              how long a staff / admin session lasts
     * @param impersonationTtl how long a super-admin "open studio as owner" session lasts
     */
    public record Jwt(String secret, @NotBlank String issuer, @NotNull Duration ttl, @NotNull Duration impersonationTtl) {
    }

    /** Origins (patterns allowed) that may call the API from a browser. */
    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /** Brute-force protection for the sign-in endpoints. */
    public record Login(@Positive int maxFailures, @NotNull Duration lockout) {
    }

    /**
     * Platform owner account bootstrap (see SuperAdminBootstrap).
     *
     * @param email           sign-in email of the super admin
     * @param initialPassword password set on first start; when blank a random one is generated and logged once
     * @param resetPassword   when true, re-applies {@code initialPassword} on every start (account recovery)
     */
    public record SuperAdmin(@NotBlank String email, String initialPassword, boolean resetPassword) {
    }
}
