package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.model.PlatformUser;
import com.atelier.media.repository.PlatformUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

/**
 * Makes the Flyway-seeded super admin usable without ever shipping a well-known password.
 * <ol>
 *   <li>The seeded row (admin@atelier.local) is renamed to {@code atelier.super-admin.email} if
 *       that differs and the seed was never activated.</li>
 *   <li>If the account has no usable password (or {@code reset-password=true}), the password is set
 *       from {@code atelier.super-admin.initial-password}; when that is blank a random one is
 *       generated and printed once in the log, and a change is forced at first sign-in.</li>
 * </ol>
 */
@Slf4j
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    static final String SEEDED_EMAIL = "admin@atelier.local";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final PlatformUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AtelierProperties props;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public SuperAdminBootstrap(PlatformUserRepository users, PasswordEncoder passwordEncoder,
                               AtelierProperties props, TransactionTemplate tx) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(status -> bootstrap());
    }

    void bootstrap() {
        AtelierProperties.SuperAdmin config = props.superAdmin();
        String email = config.email().strip().toLowerCase(Locale.ROOT);

        PlatformUser admin = users.findByEmail(email).orElse(null);
        if (admin == null) {
            PlatformUser seeded = users.findByEmail(SEEDED_EMAIL).filter(u -> !u.hasUsablePassword()).orElse(null);
            if (seeded != null) {
                seeded.setEmail(email);
                admin = seeded;
            } else {
                List<PlatformUser> existing = users.findByRole(PlatformUser.ROLE_SUPER_ADMIN);
                if (!existing.isEmpty() && existing.stream().anyMatch(PlatformUser::hasUsablePassword)) {
                    return; // a working super admin already exists under another email
                }
                admin = PlatformUser.builder()
                        .email(email)
                        .displayName("Platform Owner")
                        .passwordHash(PlatformUser.UNUSABLE_PASSWORD)
                        .build();
            }
        }

        boolean needsPassword = !admin.hasUsablePassword() || config.resetPassword();
        if (needsPassword) {
            String configured = config.initialPassword();
            boolean generated = configured == null || configured.isBlank();
            String password = generated ? randomPassword() : configured;
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setMustChangePassword(generated || admin.getMustChangePassword() == null || admin.getMustChangePassword());
            if (generated) {
                log.warn("""

                        ================================================================
                          Super admin account ready (shown once, change it after sign-in)
                            URL:      /admin
                            Email:    {}
                            Password: {}
                        ================================================================""", email, password);
            } else {
                log.info("Super admin {} password set from ATELIER_SUPERADMIN_PASSWORD", email);
            }
        }
        users.save(admin);
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder(18);
        for (int i = 0; i < 18; i++) {
            if (i > 0 && i % 6 == 0) sb.append('-');
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
