package com.atelier.media.service;

import com.atelier.media.support.ServiceHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import org.mockito.ArgumentCaptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

class S3StorageTest {
    private final ServiceHarness h = new ServiceHarness();
    @AfterEach void close() { h.close(); }

    @Test void privatePutRequiresNoStoreHeader() {
        var upload = h.storage.presignPut("restaurant-atelier-private/bistro-a/requests/1/ref.webp", "image/webp", 10);
        assertThat(upload.requiredHeaders()).containsEntry("cache-control", "private, no-store");
    }

    @Test void publicPutKeepsImmutableCaching() {
        var upload = h.storage.presignPut("restaurant-atelier/bistro-a/photo.webp", "image/webp", 10);
        assertThat(upload.requiredHeaders()).containsEntry("cache-control", "public, max-age=31536000, immutable");
    }

    @Test void privateGetSignsNoStoreResponseOverride() {
        String url = h.storage.presignGet("restaurant-atelier-private/bistro-a/requests/1/ref.webp", Duration.ofMinutes(15));
        // Inspect locally signed synthetic data only; never print bearer URLs.
        assertThat(URLDecoder.decode(url, StandardCharsets.UTF_8)).contains("response-cache-control=private, no-store");
    }

    @Test void partialDeleteDoesNotCountFailedKeyAsDeleted() {
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("b").code("AccessDenied").build()).build());
        var result = h.storage.deleteKeys("test-bucket", List.of("a", "b"));
        assertThat(result.deleted()).containsExactly("a");
        assertThat(result.failed()).containsEntry("b", "AccessDenied");
        assertThat(result.unknown()).isEmpty();
        assertThat(result.complete()).isFalse();
    }

    @Test void transportFailureDoesNotCountSubmittedKeysAsDeleted() {
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenThrow(SdkClientException.create("synthetic timeout"));
        var result = h.storage.deleteKeys("test-bucket", List.of("a"));
        assertThat(result.deleted()).isEmpty();
        assertThat(result.unknown()).containsExactly("a");
        assertThat(result.complete()).isFalse();
    }

    @Test void duplicateAndBlankKeysAreNotSubmittedTwice() {
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder().build());
        List<String> keys = new ArrayList<>(List.of("a", "a", "", " "));
        keys.add(null);
        assertThat(h.storage.deleteKeys("test-bucket", keys).deleted()).containsExactly("a");
        ArgumentCaptor<DeleteObjectsRequest> request = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(h.s3Client).deleteObjects(request.capture());
        assertThat(request.getValue().delete().objects()).hasSize(1);
    }

    @Test void batchesContinueAfterFailureWithoutLosingOutcomes() {
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenThrow(SdkClientException.create("synthetic timeout"))
                .thenReturn(DeleteObjectsResponse.builder().build());
        var result = h.storage.deleteKeys("test-bucket", IntStream.range(0, 1001).mapToObj(i -> "key-" + i).toList());
        assertThat(result.deleted()).containsExactly("key-1000");
        assertThat(result.unknown()).hasSize(1000);
        ArgumentCaptor<DeleteObjectsRequest> request = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(h.s3Client, times(2)).deleteObjects(request.capture());
        assertThat(request.getAllValues().get(0).delete().objects()).hasSize(1000);
        assertThat(request.getAllValues().get(1).delete().objects()).hasSize(1);
    }

    @Test void requestRejectionIsFailureRatherThanAcknowledgement() {
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenThrow(S3Exception.builder().statusCode(403).build());
        var result = h.storage.deleteKeys("test-bucket", List.of("a"));
        assertThat(result.failed()).containsEntry("a", "HTTP_403");
        assertThat(result.deleted()).isEmpty();
        assertThat(result.unknown()).isEmpty();
    }

    @Test void missingSdkResponseIsUnknownNotSuccess() {
        assertThat(h.storage.deleteKeys("test-bucket", List.of("a")).unknown()).containsExactly("a");
    }

    @Test void unexpectedResponseKeyDoesNotAcknowledgeRequestedObject() {
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("foreign-key").code("AccessDenied").build()).build());
        var result = h.storage.deleteKeys("test-bucket", List.of("a"));
        assertThat(result.unknown()).containsExactly("a");
        assertThat(result.deleted()).isEmpty();
    }

    @Test void singleDeleteReturnsAcknowledgementOrUnknown() {
        when(h.s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build())
                .thenThrow(SdkClientException.create("synthetic timeout"));
        assertThat(h.storage.deleteBestEffort("test-bucket", "a").deleted()).containsExactly("a");
        assertThat(h.storage.deleteBestEffort("test-bucket", "b").unknown()).containsExactly("b");
    }

    @Test void failedPrefixListingCannotReportEmptySuccess() {
        when(h.s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenThrow(S3Exception.builder().statusCode(403).build());
        var result = h.storage.deletePrefix("test-bucket", "restaurant-atelier/a/");
        assertThat(result.listingComplete()).isFalse();
        assertThat(result.complete()).isFalse();
    }

    @Test void prefixDeletionIncludesObjectFailureOutcomes() {
        when(h.s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(software.amazon.awssdk.services.s3.model.S3Object.builder().key("restaurant-atelier/a/photo").build()).build());
        when(h.s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenAnswer(call ->
                new ListObjectsV2Iterable(h.s3Client, call.getArgument(0)));
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key("restaurant-atelier/a/photo").code("AccessDenied").build()).build());
        assertThat(h.storage.deletePrefix("test-bucket", "restaurant-atelier/a/").complete()).isFalse();
    }

    @Test void publicGetDoesNotForcePrivateOverride() {
        assertThat(h.storage.presignGet("restaurant-atelier/a/photo", Duration.ofMinutes(5))).doesNotContain("response-cache-control");
    }

    @Test void explicitPrivateGetProtectsHistoricalPrefix() {
        String url = h.storage.presignPrivateGet("old-private-root/a/ref.webp", Duration.ofMinutes(15));
        assertThat(URLDecoder.decode(url, StandardCharsets.UTF_8)).contains("response-cache-control=private, no-store");
    }

    @Test void configurablePrivateRootControlsPutMetadataAndNotLookalikes() {
        var props = org.mockito.Mockito.mock(com.atelier.media.config.AtelierProperties.class);
        when(props.privateKeyPrefix()).thenReturn("/custom-private/");
        when(props.presignTtl()).thenReturn(Duration.ofMinutes(5));
        var storage = new S3Storage(h.presigner, h.s3Client, com.atelier.media.support.TestFixtures.awsProperties(), props);
        assertThat(storage.presignPut("custom-private/a/ref", "image/webp", 1).requiredHeaders())
                .containsEntry("cache-control", "private, no-store");
        assertThat(storage.presignPut("custom-private-lookalike/a/ref", "image/webp", 1).requiredHeaders())
                .containsEntry("cache-control", "public, max-age=31536000, immutable");
    }

    @Test void emptyDeleteDoesNotMakeAnS3Request() {
        assertThat(h.storage.deleteKeys("test-bucket", List.of()).complete()).isTrue();
        verify(h.s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test void serverFailureIsUnknownAndResultFormattingDoesNotLeakKeys() {
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        var result = h.storage.deleteKeys("test-bucket", List.of("private-reference-identifier"));
        assertThat(result.unknown()).containsExactly("private-reference-identifier");
        assertThat(result.failed()).isEmpty();
        assertThat(result.toString()).doesNotContain("private-reference-identifier");
    }

    @Test void requestTimeoutRemainsUnknownEvenWithAnHttpStatus() {
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenThrow(S3Exception.builder().statusCode(408).build());
        assertThat(h.storage.deleteKeys("test-bucket", List.of("a")).unknown()).containsExactly("a");
    }

    @Test void partialListingPreservesDiscoveredOutcomesAndReportsIncomplete() {
        var paginator = org.mockito.Mockito.mock(ListObjectsV2Iterable.class);
        when(h.s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(paginator);
        when(paginator.contents()).thenReturn(() -> new java.util.Iterator<software.amazon.awssdk.services.s3.model.S3Object>() {
            private boolean yielded;
            public boolean hasNext() {
                if (yielded) throw SdkClientException.create("synthetic listing interruption");
                return true;
            }
            public software.amazon.awssdk.services.s3.model.S3Object next() {
                yielded = true;
                return software.amazon.awssdk.services.s3.model.S3Object.builder().key("known-key").build();
            }
        });
        when(h.s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder().build());
        var result = h.storage.deletePrefix("test-bucket", "restaurant-atelier/a/");
        assertThat(result.deleted()).containsExactly("known-key");
        assertThat(result.listingComplete()).isFalse();
        assertThat(result.complete()).isFalse();
    }
}
