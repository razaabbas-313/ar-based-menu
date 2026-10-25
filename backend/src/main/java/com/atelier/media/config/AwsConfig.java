package com.atelier.media.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Real AWS S3 clients. Neither bean makes a network call at startup. */
@Slf4j
@Configuration
public class AwsConfig {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(AwsProperties props) {
        if (props.hasStaticCredentials()) {
            log.info("AWS: using AWS_ACCESS_KEY_ID ...{} for region {}",
                    props.accessKeyId().substring(Math.max(0, props.accessKeyId().length() - 4)), props.region());
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey()));
        }
        log.info("AWS: AWS_ACCESS_KEY_ID not set, using the default credential provider chain");
        return DefaultCredentialsProvider.create();
    }

    /** Signs PUT URLs locally (SigV4). No file bytes ever touch this JVM. */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(AwsProperties props, AwsCredentialsProvider credentials) {
        if (!props.isBucketConfigured()) {
            log.warn("AWS_S3_BUCKET is not set: menus and sign-in work, but uploads will answer 503 until it is configured.");
        } else {
            log.info("AWS: uploads go to s3://{} ({})", props.s3().bucket(), props.region());
        }
        return S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials)
                .build();
    }

    /** Used only for small control-plane calls: HEAD (verify uploads) and DELETE. */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(AwsProperties props, AwsCredentialsProvider credentials) {
        return S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials)
                .build();
    }
}
