package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.security.CaffeineLoginThrottle;

/**
 * Service adapter delegating to {@link CaffeineLoginThrottle} for backwards compatibility and tests.
 */
public class LoginAttemptService extends CaffeineLoginThrottle {

    public LoginAttemptService(AtelierProperties properties) {
        super(properties);
    }
}
