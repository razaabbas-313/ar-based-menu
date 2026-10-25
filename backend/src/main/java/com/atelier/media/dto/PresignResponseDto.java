package com.atelier.media.dto;

import java.time.Instant;
import java.util.Map;

/**
 * @param uploadUrl        signed S3 PUT URL
 * @param objectKey        S3 key the client must echo back in the confirm call
 * @param s3Url            public URL the object will be served from once uploaded
 * @param mediaId          database id of the PRESIGNED record
 * @param expiresAt        absolute expiry (server clock)
 * @param expiresInSeconds lifetime relative to issuance. Clients should count down from this
 *                         rather than compare {@code expiresAt} with a possibly skewed device clock.
 * @param requiredHeaders  headers that were signed into the URL; the PUT must send them verbatim
 */
public record PresignResponseDto(
        String uploadUrl,
        String objectKey,
        String s3Url,
        Long mediaId,
        Instant expiresAt,
        long expiresInSeconds,
        Map<String, String> requiredHeaders
) {
}
