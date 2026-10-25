package com.atelier.media.model;

/** Lifecycle of a direct-to-S3 upload. */
public enum UploadStatus {
    /** A signed PUT URL was issued; the browser has not confirmed the upload yet. */
    PRESIGNED,
    /** S3 holds the object and its size was verified with a HEAD request. */
    COMPLETED,
    /** The token expired unused, or S3 verification failed. */
    FAILED
}
