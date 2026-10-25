package com.atelier.media.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process menu cache, partitioned per tenant: key = restaurant slug, value = that
 * restaurant's published menu. Staff writes evict only their own restaurant's entry; the TTL is
 * a safety net for changes made by other instances or directly in the database.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String MENU_CACHE = "restaurantMenu";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(MENU_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats());
        manager.setAllowNullValues(false);
        return manager;
    }
}
