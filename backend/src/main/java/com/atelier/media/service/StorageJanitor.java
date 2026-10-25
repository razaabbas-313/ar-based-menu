package com.atelier.media.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** Background S3 cleanup after a restaurant is hard-deleted (never blocks the admin request). */
@Slf4j
@Component
public class StorageJanitor {

    private final S3Storage storage;

    public StorageJanitor(S3Storage storage) {
        this.storage = storage;
    }

    /**
     * Deletes every key the database knew about (covers objects uploaded under older slugs) and
     * then sweeps the tenant's current folder for anything left behind.
     */
    @Async
    public void wipeRestaurantAssets(String bucket, Collection<String> knownKeys, String tenantPrefix) {
        try {
            int known = storage.deleteKeys(bucket, knownKeys);
            int swept = storage.deletePrefix(bucket, tenantPrefix);
            log.info("Storage wipe finished for {}: {} known object(s), {} swept from prefix", tenantPrefix, known, swept);
        } catch (RuntimeException e) {
            log.error("Storage wipe for {} failed; objects may remain in s3://{}/{}", tenantPrefix, bucket, tenantPrefix, e);
        }
    }
}
