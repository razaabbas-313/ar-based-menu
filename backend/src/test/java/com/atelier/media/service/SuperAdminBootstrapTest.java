package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.model.PlatformUser;
import com.atelier.media.repository.PlatformUserRepository;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SuperAdminBootstrapTest {

    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));

    private SuperAdminBootstrap bootstrap(String email, String password, boolean reset) {
        AtelierProperties base = TestFixtures.atelierProperties();
        AtelierProperties props = new AtelierProperties(base.keyPrefix(), base.privateKeyPrefix(), base.presignTtl(), base.staleUploadTtl(),
                base.maxImageBytes(), base.maxModelBytes(), base.maxPublishedModelBytes(), base.maxBrandingBytes(), base.menuLimit(), base.directoryLimit(),
                base.jwt(), base.cors(), base.login(), new AtelierProperties.SuperAdmin(email, password, reset),
                base.passwordReset(), base.trustedProxies());
        when(users.save(any(PlatformUser.class))).thenAnswer(inv -> inv.getArgument(0));
        return new SuperAdminBootstrap(users, encoder, props, tx);
    }

    private static PlatformUser seeded() {
        return PlatformUser.builder().id(1L).email("admin@atelier.local").displayName("Platform Owner")
                .passwordHash(PlatformUser.UNUSABLE_PASSWORD).mustChangePassword(true).build();
    }

    @Test
    void seededAccountWithoutConfiguredPasswordGetsARandomOneAndMustChangeIt() {
        PlatformUser admin = seeded();
        when(users.findByEmail("admin@atelier.local")).thenReturn(Optional.of(admin));

        bootstrap("admin@atelier.local", null, false).bootstrap();

        assertThat(admin.hasUsablePassword()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2a$");
        assertThat(admin.getMustChangePassword()).isTrue();
    }

    @Test
    void configuredPasswordAndEmailAreApplied() {
        PlatformUser admin = seeded();
        when(users.findByEmail("owner@platform.test")).thenReturn(Optional.empty());
        when(users.findByEmail("admin@atelier.local")).thenReturn(Optional.of(admin));

        bootstrap("Owner@Platform.test", "correct-horse-battery", false).bootstrap();

        assertThat(admin.getEmail()).isEqualTo("owner@platform.test");
        assertThat(encoder.matches("correct-horse-battery", admin.getPasswordHash())).isTrue();
    }

    @Test
    void anExistingPasswordIsNeverOverwrittenUnlessResetIsRequested() {
        PlatformUser admin = seeded();
        admin.setPasswordHash(encoder.encode("already-set-password"));
        when(users.findByEmail("admin@atelier.local")).thenReturn(Optional.of(admin));
        when(users.findByRole(PlatformUser.ROLE_SUPER_ADMIN)).thenReturn(List.of(admin));

        bootstrap("admin@atelier.local", "new-password-123", false).bootstrap();
        assertThat(encoder.matches("already-set-password", admin.getPasswordHash())).isTrue();

        bootstrap("admin@atelier.local", "new-password-123", true).bootstrap();
        assertThat(encoder.matches("new-password-123", admin.getPasswordHash())).isTrue();
    }
}
