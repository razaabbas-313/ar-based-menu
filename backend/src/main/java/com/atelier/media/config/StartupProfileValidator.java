package com.atelier.media.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Validates active profile and required secrets on startup.
 * Prevents accidental deployment of 'local' profile to hosted environments (Render, Vercel, EC2, etc.),
 * and ensures required production secrets (such as ATELIER_JWT_SECRET) are configured.
 */
@Slf4j
@Component
public class StartupProfileValidator implements ApplicationRunner {

    private final Environment environment;

    public StartupProfileValidator(Environment environment) {
        this.environment = environment;
        validate();
    }

    public void validate() {
        boolean isLocal = isProfileActive("local");
        if (isLocal) {
            boolean isHosted = isSet("RENDER")
                    || isSet("VERCEL")
                    || isSet("PORT")
                    || "production".equalsIgnoreCase(get("ATELIER_ENV"));

            if (isHosted) {
                String error = "Refusing to start with 'local' profile in hosted environment (detected RENDER, VERCEL, PORT, or ATELIER_ENV=production). Use 'prod' profile.";
                log.error(error);
                throw new IllegalStateException(error);
            }
        }

        boolean isProd = isProfileActive("prod");
        if (isProd) {
            String jwtSecret = get("ATELIER_JWT_SECRET");
            if (jwtSecret == null || jwtSecret.isBlank()) {
                jwtSecret = environment.getProperty("atelier.jwt.secret");
            }
            if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                String error = "ATELIER_JWT_SECRET must be configured with at least 32 bytes in production.";
                log.error(error);
                throw new IllegalStateException(error);
            }
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    private boolean isProfileActive(String profileName) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles != null && activeProfiles.length > 0) {
            return Arrays.stream(activeProfiles).anyMatch(p -> profileName.equalsIgnoreCase(p.strip()));
        }
        String[] defaultProfiles = environment.getDefaultProfiles();
        if (defaultProfiles != null) {
            return Arrays.stream(defaultProfiles).anyMatch(p -> profileName.equalsIgnoreCase(p.strip()));
        }
        return false;
    }

    private boolean isSet(String key) {
        String prop = environment.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return true;
        }
        String env = System.getenv(key);
        return env != null && !env.isBlank();
    }

    private String get(String key) {
        String prop = environment.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        return System.getenv(key);
    }
}
