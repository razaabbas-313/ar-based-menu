package com.atelier.media.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Presigned URLs that were never confirmed (closed tab, lost signal) are marked FAILED so the
 * table stays truthful, and any orphaned object is removed from the bucket.
 * Also performs scheduled cleanup of temporary 3D reference photos 14 days after model approval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleUploadReaper {

    private final MediaService mediaService;
    private final ModelRequestService modelRequestService;

    @Scheduled(fixedDelayString = "${atelier.reaper-interval:PT5M}", initialDelayString = "PT1M")
    public void reap() {
        try {
            int expired = mediaService.expireStalePresignedUploads();
            if (expired > 0) {
                log.info("Marked {} unconfirmed presigned upload(s) as FAILED", expired);
            }
        } catch (RuntimeException e) {
            log.warn("Stale-upload sweep failed; will retry next run", e);
        }

        try {
            int cleaned = modelRequestService.cleanupReferencePhotos();
            if (cleaned > 0) {
                log.info("Cleaned temporary 3D reference photos for {} approved dish(es)", cleaned);
            }
        } catch (RuntimeException e) {
            log.warn("3D reference photo sweep failed; will retry next run", e);
        }
    }
}
