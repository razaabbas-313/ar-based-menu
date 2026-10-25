package com.atelier.media.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashingTest {

    /** The production bean, not a hand-built encoder. */
    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void hashIsBcryptWithCost12AndNeverThePlainPassword() {
        String hash = encoder.encode("truffle-and-saffron");

        assertThat(hash).startsWith("$2a$12$").doesNotContain("truffle");
        assertThat(hash).hasSize(60);
    }

    @Test
    void matchesOnlyTheOriginalPassword() {
        String hash = encoder.encode("truffle-and-saffron");

        assertThat(encoder.matches("truffle-and-saffron", hash)).isTrue();
        assertThat(encoder.matches("Truffle-and-saffron", hash)).isFalse();
        assertThat(encoder.matches("", hash)).isFalse();
    }

    @Test
    void samePasswordHashesDifferentlyEachTime() {
        String first = encoder.encode("same-password-123");
        String second = encoder.encode("same-password-123");

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("same-password-123", first)).isTrue();
        assertThat(encoder.matches("same-password-123", second)).isTrue();
    }

    @Test
    void malformedStoredHashNeverMatches() {
        // The V2 migration's legacy placeholder restaurant uses "!" so nobody can sign in as it.
        assertThat(encoder.matches("anything", "!")).isFalse();
    }
}
