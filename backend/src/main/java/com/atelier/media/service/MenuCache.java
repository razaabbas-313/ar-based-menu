package com.atelier.media.service;

import com.atelier.media.config.CacheConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/** Evicts per-restaurant public menu entries (key = slug). */
@Component
public class MenuCache {

    private final CacheManager cacheManager;

    public MenuCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evict(String... slugs) {
        Cache cache = cacheManager.getCache(CacheConfig.MENU_CACHE);
        if (cache == null) {
            return;
        }
        for (String slug : slugs) {
            if (slug != null && !slug.isBlank()) {
                cache.evict(slug);
            }
        }
    }
}
