package com.atelier.media.security;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.exception.TooManyAttemptsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caffeine-backed login rate limiter and brute-force throttle.
 * Throttles by (email/key + client IP) as well as by client IP alone (across all accounts).
 * Memory entries automatically expire after {@code lockout} duration.
 */
@Slf4j
@Component
public class CaffeineLoginThrottle implements LoginThrottle {

    public static final int MAX_IP_FAILURES = 20;

    private final AtelierProperties.Login config;
    private final Cache<String, AtomicInteger> emailIpFailures;
    private final Cache<String, AtomicInteger> ipFailures;

    public CaffeineLoginThrottle(AtelierProperties properties) {
        this.config = properties.login();
        this.emailIpFailures = Caffeine.newBuilder()
                .expireAfterWrite(config.lockout())
                .maximumSize(100_000)
                .build();
        this.ipFailures = Caffeine.newBuilder()
                .expireAfterWrite(config.lockout())
                .maximumSize(100_000)
                .build();
    }

    @Override
    public void checkAllowed(String key, String clientIp) {
        // 1. Check IP-wide limit across all accounts (credential stuffing defense)
        if (clientIp != null && !clientIp.isBlank() && !"unknown".equalsIgnoreCase(clientIp)) {
            AtomicInteger ipCount = ipFailures.getIfPresent(clientIp.trim());
            if (ipCount != null && ipCount.get() >= MAX_IP_FAILURES) {
                log.warn("Login throttle: IP {} exceeded failure threshold ({}/{})", clientIp, ipCount.get(), MAX_IP_FAILURES);
                throw new TooManyAttemptsException(config.lockout());
            }
        }

        // 2. Check (key + IP) limit
        String throttleKey = compositeKey(key, clientIp);
        AtomicInteger count = emailIpFailures.getIfPresent(throttleKey);
        if (count != null && count.get() >= config.maxFailures()) {
            log.warn("Login throttle: Key [{}] from IP {} exceeded failure threshold ({}/{})", key, clientIp, count.get(), config.maxFailures());
            throw new TooManyAttemptsException(config.lockout());
        }
    }

    @Override
    public void recordFailure(String key, String clientIp) {
        // Increment IP failures
        if (clientIp != null && !clientIp.isBlank() && !"unknown".equalsIgnoreCase(clientIp)) {
            AtomicInteger ipCount = ipFailures.get(clientIp.trim(), k -> new AtomicInteger());
            ipCount.incrementAndGet();
            ipFailures.put(clientIp.trim(), ipCount);
        }

        // Increment (key + IP) failures
        String throttleKey = compositeKey(key, clientIp);
        AtomicInteger count = emailIpFailures.get(throttleKey, k -> new AtomicInteger());
        int failures = count.incrementAndGet();
        emailIpFailures.put(throttleKey, count);

        // Progressive delay: 500ms after 3, 1000ms after 4, 2000ms after 5+
        applyProgressiveDelay(failures);
    }

    @Override
    public void recordSuccess(String key, String clientIp) {
        emailIpFailures.invalidate(compositeKey(key, clientIp));
    }

    private void applyProgressiveDelay(int failures) {
        long delayMs = 0;
        if (failures == 3) {
            delayMs = 500;
        } else if (failures == 4) {
            delayMs = 1000;
        } else if (failures >= 5) {
            delayMs = 2000;
        }
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String compositeKey(String key, String clientIp) {
        String safeKey = key == null ? "" : key.trim().toLowerCase();
        String safeIp = clientIp == null ? "unknown" : clientIp.trim();
        return safeKey + "|" + safeIp;
    }
}
