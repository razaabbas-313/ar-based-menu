package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.config.AwsProperties;
import com.atelier.media.exception.StorageNotConfiguredException;
import com.atelier.media.exception.UploadVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Thin wrapper around the real AWS S3 clients: key layout, presigned PUTs, HEAD verification and
 * deletes. Callers must never invoke the network methods while a database transaction is open.
 */
@Slf4j
@Component
public class S3Storage {

    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final int DELETE_BATCH = 1000;

    private final S3Presigner presigner;
    private final S3Client client;
    private final AwsProperties aws;
    private final AtelierProperties props;

    public S3Storage(S3Presigner presigner, S3Client client, AwsProperties aws, AtelierProperties props) {
        this.presigner = presigner;
        this.client = client;
        this.aws = aws;
        this.props = props;
    }

    public record PresignedUpload(String url, Instant expiresAt, long expiresInSeconds, Map<String, String> requiredHeaders) {
    }

    public void requireConfigured() {
        if (!aws.isBucketConfigured()) {
            throw new StorageNotConfiguredException();
        }
    }

    public String bucket() {
        return aws.s3().bucket();
    }

    /** {@code restaurant-atelier/{slug}/} */
    public String tenantPrefix(String slug) {
        return trimSlashes(props.keyPrefix()) + "/" + slug + "/";
    }

    /** {@code restaurant-atelier-private/{slug}/} */
    public String privateTenantPrefix(String slug) {
        return trimSlashes(props.privateKeyPrefix()) + "/" + slug + "/";
    }

    /** {@code restaurant-atelier/{slug}/{segment}/{namePrefix}{uuid}.{ext}} */
    public String newKey(String slug, String segment, String namePrefix, String ext) {
        return tenantPrefix(slug) + segment + "/" + (namePrefix == null ? "" : namePrefix) + UUID.randomUUID() + "." + ext;
    }

    public String publicUrl(String key) {
        String base = aws.s3().publicBaseUrl();
        if (base != null && !base.isBlank()) {
            String b = base.strip();
            return (b.endsWith("/") ? b.substring(0, b.length() - 1) : b) + "/" + key;
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket(), aws.region(), key);
    }

    /** Presigns a GET URL with custom TTL (e.g. 15 minutes) for private assets without reading bytes into RAM. */
    public String presignGet(String key, Duration duration) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket())
                .key(key)
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(get)
                .build());
        return presigned.url().toString();
    }

    /** Local SigV4 signing; locks content type, exact length and Cache-Control into the URL. */
    public PresignedUpload presignPut(String key, String contentType, long sizeBytes) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .cacheControl(IMMUTABLE_CACHE_CONTROL)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(props.presignTtl())
                .putObjectRequest(put)
                .build());
        return new PresignedUpload(presigned.url().toString(), presigned.expiration(),
                props.presignTtl().toSeconds(), browserSettableHeaders(presigned.signedHeaders()));
    }

    /** HEAD the object; a missing object becomes a user-readable verification error. */
    public HeadObjectResponse head(String bucket, String key) {
        try {
            return client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw notUploaded(key);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw notUploaded(key);
            }
            throw e;
        }
    }

    public HeadObjectResponse head(String key) {
        return head(bucket(), key);
    }

    public void deleteQuietly(String bucket, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("Could not delete s3://{}/{}: {}", bucket, key, e.getMessage());
        }
    }

    public void deleteQuietly(String key) {
        deleteQuietly(bucket(), key);
    }

    /** Batch delete (1000 keys per request). Returns the number of keys submitted. */
    public int deleteKeys(String bucket, Collection<String> keys) {
        Set<String> unique = new LinkedHashSet<>();
        keys.stream().filter(k -> k != null && !k.isBlank()).forEach(unique::add);
        List<String> all = new ArrayList<>(unique);
        for (int i = 0; i < all.size(); i += DELETE_BATCH) {
            List<ObjectIdentifier> batch = all.subList(i, Math.min(all.size(), i + DELETE_BATCH)).stream()
                    .map(k -> ObjectIdentifier.builder().key(k).build())
                    .toList();
            try {
                client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(batch).quiet(true).build())
                        .build());
            } catch (SdkException e) {
                log.warn("Batch delete of {} object(s) in {} failed: {}", batch.size(), bucket, e.getMessage());
            }
        }
        return all.size();
    }

    /** Lists every object under {@code prefix} and deletes them. Requires s3:ListBucket. */
    public int deletePrefix(String bucket, String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            for (S3Object object : client.listObjectsV2Paginator(
                    ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build()).contents()) {
                keys.add(object.key());
            }
        } catch (SdkException e) {
            log.warn("Could not list s3://{}/{} (grant s3:ListBucket to sweep whole folders): {}", bucket, prefix, e.getMessage());
            return 0;
        }
        return deleteKeys(bucket, keys);
    }

    private static UploadVerificationException notUploaded(String key) {
        return new UploadVerificationException("S3 has no object at " + key + ". The upload did not finish; please retry.");
    }

    /** Signed headers minus the ones browsers set themselves (and refuse to let scripts set). */
    private static Map<String, String> browserSettableHeaders(Map<String, List<String>> signed) {
        Map<String, String> headers = new LinkedHashMap<>();
        signed.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.equals("host") && !lower.equals("content-length") && values != null && !values.isEmpty()) {
                headers.put(name, String.join(",", values));
            }
        });
        return headers;
    }

    private static String trimSlashes(String value) {
        String v = value.strip();
        while (v.startsWith("/")) v = v.substring(1);
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }
}
