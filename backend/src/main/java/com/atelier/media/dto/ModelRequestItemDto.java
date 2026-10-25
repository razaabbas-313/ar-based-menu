package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ModelRequestItemDto(
        @NotBlank
        @Pattern(regexp = "^(front(_45)?|top(_90)?|back(_45)?|left(_side)?|right(_side)?)$", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Slot must be one of: front, top, back, left, right")
        String slot,

        @NotBlank
        @Size(max = 255, message = "Filename must not exceed 255 characters")
        String filename,

        @NotBlank
        @Pattern(regexp = "^(image/webp|image/jpeg|image/png)$", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Content type must be image/webp, image/jpeg, or image/png")
        String contentType,

        @NotNull
        @Positive
        Long uploadSizeBytes
) {
}
