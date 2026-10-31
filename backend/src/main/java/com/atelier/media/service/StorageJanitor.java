package com.atelier.media.service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Collection;
/** Notification only: exact-key transactional manifests own physical deletion, never prefix sweeps. */
@Slf4j
@Component
public class StorageJanitor {
    public StorageJanitor(S3Storage storage) {}
    public void wipeRestaurantAssets(String bucket, Collection<String> knownKeys, String tenantPrefix) {
        log.info("Restaurant logically deleted; durable manifests await cleanup worker settlement");
    }
}