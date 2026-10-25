package com.atelier.media.dto;

import java.util.List;

public record ModelRequestConfirmDto(
        List<UploadedPhotoRef> photos,
        String notes
) {
    public record UploadedPhotoRef(String slot, String s3Key, String s3Url) {}
}
