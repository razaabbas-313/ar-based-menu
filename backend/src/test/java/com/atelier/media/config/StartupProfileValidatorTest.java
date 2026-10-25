package com.atelier.media.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupProfileValidatorTest {

    @Test
    void startingInProdProfileWithoutJwtSecretFailsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        // No ATELIER_JWT_SECRET or atelier.jwt.secret set

        assertThatThrownBy(() -> new StartupProfileValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ATELIER_JWT_SECRET must be configured with at least 32 bytes in production");
    }

    @Test
    void startingInProdProfileWithShortJwtSecretFailsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("ATELIER_JWT_SECRET", "too-short");

        assertThatThrownBy(() -> new StartupProfileValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ATELIER_JWT_SECRET must be configured with at least 32 bytes in production");
    }

    @Test
    void startingInProdProfileWithValidJwtSecretSucceeds() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("ATELIER_JWT_SECRET", "a-very-long-production-jwt-secret-with-at-least-32-bytes");

        assertThatCode(() -> new StartupProfileValidator(env))
                .doesNotThrowAnyException();
    }

    @Test
    void startingWithLocalProfileWhenRenderIsSetFailsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("RENDER", "true");

        assertThatThrownBy(() -> new StartupProfileValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start with 'local' profile in hosted environment");
    }

    @Test
    void startingWithLocalProfileWhenVercelIsSetFailsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("VERCEL", "1");

        assertThatThrownBy(() -> new StartupProfileValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start with 'local' profile in hosted environment");
    }

    @Test
    void startingWithLocalProfileWhenPortIsSetFailsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("PORT", "10000");

        assertThatThrownBy(() -> new StartupProfileValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start with 'local' profile in hosted environment");
    }

    @Test
    void startingWithLocalProfileWhenAtelierEnvIsProductionFailsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        env.setProperty("ATELIER_ENV", "production");

        assertThatThrownBy(() -> new StartupProfileValidator(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start with 'local' profile in hosted environment");
    }

    @Test
    void startingWithLocalProfileInDevWithoutHostedVarsSucceeds() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        assertThatCode(() -> new StartupProfileValidator(env))
                .doesNotThrowAnyException();
    }
}
