package com.atelier.media.model;

/**
 * What kind of asset a media record represents.
 * <p>
 * {@code DAILY_SPECIAL} may carry either a 2D photo or a 3D model; the renderer is
 * chosen by the stored content type, so the flag only drives merchandising.
 */
public enum MediaType {
    IMAGE_2D("image-2d"),
    MODEL_3D("model-3d"),
    DAILY_SPECIAL("daily-special");

    private final String keySegment;

    MediaType(String keySegment) {
        this.keySegment = keySegment;
    }

    /** Folder name used inside the S3 key, e.g. {@code restaurant-atelier/model-3d/...}. */
    public String keySegment() {
        return keySegment;
    }
}
