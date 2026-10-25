package com.atelier.media.security;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.exception.InvalidTokenException;
import com.atelier.media.model.PlatformUser;
import com.atelier.media.model.Restaurant;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private final AtelierProperties props = TestFixtures.atelierProperties();
    private final SecretKey key = JwtKeys.secretKey(props.jwt().secret());
    private final JwtDecoder decoder = JwtKeys.decoder(key, props.jwt().issuer());
    private final TokenService tokens = new TokenService(JwtKeys.encoder(key), props, Clock.systemUTC());

    @Test
    void issuedTokenCarriesTenantClaimsAndValidates() {
        TokenService.IssuedToken issued = tokens.issue(TestFixtures.restaurant(42L, "bistro-lumiere"));

        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("atelier-media-engine");
        assertThat(((Number) jwt.getClaim(Tenant.CLAIM_RESTAURANT_ID)).longValue()).isEqualTo(42L);
        assertThat(jwt.getClaimAsString(Tenant.CLAIM_SLUG)).isEqualTo("bistro-lumiere");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo(TokenService.STUDIO_SCOPE);
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(issued.expiresInSeconds()).isEqualTo(Duration.ofHours(12).toSeconds());

        assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly(TokenService.ROLE_RESTAURANT);
        assertThat(jwt.getClaimAsString(TokenService.IMPERSONATED_BY_CLAIM)).isNull();

        Tenant tenant = Tenant.from(jwt);
        assertThat(tenant.restaurantId()).isEqualTo(42L);
        assertThat(tenant.slug()).isEqualTo("bistro-lumiere");
    }

    @Test
    void adminTokenCarriesSuperAdminRoleAndNoTenant() {
        PlatformUser admin = PlatformUser.builder().id(3L).email("owner@platform.test").displayName("Owner").passwordHash("x").build();

        Jwt jwt = decoder.decode(tokens.issueAdmin(admin).value());

        assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly(TokenService.ROLE_SUPER_ADMIN);
        assertThat(AdminPrincipal.from(jwt)).isEqualTo(new AdminPrincipal(3L, "owner@platform.test"));
        assertThatThrownBy(() -> Tenant.from(jwt)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void impersonationTokenIsShortLivedAndNamesTheAdmin() {
        TokenService.IssuedToken issued = tokens.issueImpersonation(TestFixtures.restaurant(42L, "bistro-lumiere"), "owner@platform.test");

        Jwt jwt = decoder.decode(issued.value());

        assertThat(issued.expiresInSeconds()).isEqualTo(Duration.ofHours(1).toSeconds());
        assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly(TokenService.ROLE_RESTAURANT);
        assertThat(jwt.getClaimAsString(TokenService.IMPERSONATED_BY_CLAIM)).isEqualTo("owner@platform.test");
    }

    @Test
    void temporaryPasswordTokensOnlyCarryTheSetupRole() {
        Restaurant fresh = TestFixtures.restaurant(42L, "bistro-lumiere");
        fresh.setMustChangePassword(true);

        Jwt setup = decoder.decode(tokens.issue(fresh).value());
        assertThat(setup.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly(TokenService.ROLE_RESTAURANT_SETUP);
        assertThat(setup.getClaimAsBoolean(TokenService.MUST_CHANGE_PASSWORD_CLAIM)).isTrue();

        // Super-admin support sessions are never downgraded by the owner's pending reset.
        Jwt support = decoder.decode(tokens.issueImpersonation(fresh, "owner@platform.test").value());
        assertThat(support.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly(TokenService.ROLE_RESTAURANT);
        assertThat(support.getClaimAsBoolean(TokenService.MUST_CHANGE_PASSWORD_CLAIM)).isFalse();
        assertThat(Tenant.from(support).impersonated()).isTrue();

        fresh.setMustChangePassword(false);
        Jwt full = decoder.decode(tokens.issue(fresh).value());
        assertThat(full.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly(TokenService.ROLE_RESTAURANT);
        assertThat(full.getClaimAsBoolean(TokenService.MUST_CHANGE_PASSWORD_CLAIM)).isFalse();
        assertThat(Tenant.from(full).impersonated()).isFalse();
    }

    @Test
    void restaurantTokenIsNotAnAdminPrincipal() {
        Jwt jwt = decoder.decode(tokens.issue(TestFixtures.restaurant(1L, "bistro-a")).value());

        assertThatThrownBy(() -> AdminPrincipal.from(jwt)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = tokens.issue(TestFixtures.restaurant(1L, "bistro-a")).value();
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        String forged = parts[0] + "." + parts[1] + "." + (first == 'A' ? 'B' : 'A') + parts[2].substring(1);

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        AtelierProperties other = TestFixtures.atelierProperties(
                "a-completely-different-secret-of-sufficient-length-42", TestFixtures.ISSUER, Duration.ofHours(1));
        SecretKey otherKey = JwtKeys.secretKey(other.jwt().secret());
        String foreign = new TokenService(JwtKeys.encoder(otherKey), other, Clock.systemUTC())
                .issue(TestFixtures.restaurant(1L, "bistro-a")).value();

        assertThatThrownBy(() -> decoder.decode(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        AtelierProperties otherIssuer = TestFixtures.atelierProperties(
                TestFixtures.JWT_SECRET, "someone-else", Duration.ofHours(1));
        String token = new TokenService(JwtKeys.encoder(key), otherIssuer, Clock.systemUTC())
                .issue(TestFixtures.restaurant(1L, "bistro-a")).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        Clock yesterday = Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC);
        String token = new TokenService(JwtKeys.encoder(key), props, yesterday)
                .issue(TestFixtures.restaurant(1L, "bistro-a")).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretsAreRefusedAtStartup() {
        assertThatThrownBy(() -> JwtKeys.secretKey("too-short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtKeys.secretKey(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtKeys.secretKey("")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tenantRequiresRestaurantClaims() {
        Jwt withoutTenant = Jwt.withTokenValue("t").header("alg", "HS256").claim("sub", "x").build();

        assertThatThrownBy(() -> Tenant.from(withoutTenant)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> Tenant.from(null)).isInstanceOf(InvalidTokenException.class);
    }
}
