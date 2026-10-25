package com.atelier.media.exception;

/** Uploads were requested but AWS_S3_BUCKET is not configured. */
public class StorageNotConfiguredException extends RuntimeException {
    public StorageNotConfiguredException() {
        super("Cloud storage is not configured on the server. Set AWS_S3_BUCKET, AWS_REGION, "
                + "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY, then restart the API.");
    }
}
