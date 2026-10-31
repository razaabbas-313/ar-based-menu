package com.atelier.media.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Presigned URLs that were never confirmed (closed tab, lost signal) are marked FAILED so the
 * table stays truthful, with cleanup intent committed in the same transaction.
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
                log.info("Expired {} pending upload record(s); physical S3 cleanup may remain incomplete", expired);
            }
        } catch (RuntimeException e) {
            log.warn("Stale-upload sweep failed; will retry next run ({})", e.getClass().getSimpleName());
        }

        try {
            modelRequestService.expirePendingReferences();
            int cleaned = modelRequestService.cleanupReferencePhotos();
            if (cleaned > 0) {
                log.info("Scheduled durable reference cleanup for {} approved dish(es)", cleaned);
            }
        } catch (RuntimeException e) {
            log.warn("Reference scheduling failed; will retry next run ({})", e.getClass().getSimpleName());
        }
    }
}
