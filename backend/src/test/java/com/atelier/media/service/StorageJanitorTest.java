package com.atelier.media.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class StorageJanitorTest {
    @Test void logicalDeletionNeverStartsPrefixSweep(CapturedOutput output) {
        S3Storage storage = mock(S3Storage.class);
        new StorageJanitor(storage).wipeRestaurantAssets("bucket", List.of("a"), "prefix/");
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deletePrefix(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteKeys(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyCollection());
        assertThat(output.getOut()).contains("durable manifests await cleanup worker settlement").doesNotContain("Storage wipe acknowledged");
    }

    @Test void notificationDoesNotClaimPhysicalDeletion(CapturedOutput output) {
        S3Storage storage = mock(S3Storage.class);
        new StorageJanitor(storage).wipeRestaurantAssets("bucket", List.of("a"), "prefix/");
        assertThat(output.getOut()).contains("durable manifests await cleanup worker settlement").doesNotContain("Storage wipe acknowledged");
    }
}
