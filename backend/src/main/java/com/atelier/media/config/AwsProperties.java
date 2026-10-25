package com.atelier.media.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code aws.*} (real AWS S3 only) from the standard environment variables:
 * AWS_REGION, AWS_S3_BUCKET, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_S3_PUBLIC_BASE_URL.
 * <p>
 * Leave the access key pair blank to use the default AWS credential chain
 * (~/.aws/credentials profile, SSO, ECS task role, EC2 instance profile).
 */
@Validated
@ConfigurationProperties(prefix = "aws")
public record AwsProperties(
        @NotBlank String region,
        String accessKeyId,
        String secretAccessKey,
        @Valid @NotNull S3 s3
) {

    /**
     * @param bucket        S3 bucket name. Blank is allowed so the API can boot without storage;
     *                      upload endpoints then answer 503 with a clear message.
     * @param publicBaseUrl optional CloudFront or bucket website base URL used to build public links
     */
    public record S3(String bucket, String publicBaseUrl) {
    }

    public boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank();
    }

    public boolean isBucketConfigured() {
        return s3 != null && s3.bucket() != null && !s3.bucket().isBlank();
    }
}
