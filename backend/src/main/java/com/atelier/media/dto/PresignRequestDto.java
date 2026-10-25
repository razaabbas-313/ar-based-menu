package com.atelier.media.dto;

import com.atelier.media.model.DishCategory;
import com.atelier.media.model.MediaType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Creates a new dish from its 2D photo. A 3D model is attached afterwards with
 * {@code POST /api/v1/studio/dishes/{id}/attach-3d}. The owning restaurant comes from the JWT.
 *
 * @param mediaType         IMAGE_2D or DAILY_SPECIAL
 * @param originalSizeBytes size of the user's original photo before client-side compression
 * @param uploadSizeBytes   exact size of the compressed bytes that will be PUT; signed into the URL
 * @param dietaryTags       see {@code DietaryTags.ALLOWED}
 */
public record PresignRequestDto(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 64) String contentType,
        @NotBlank @Size(max = 160) String dishTitle,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal priceTag,
        DishCategory dishCategory,
        @NotNull MediaType mediaType,
        @Positive Long originalSizeBytes,
        @NotNull @Positive Long uploadSizeBytes,
        @Pattern(regexp = "table|floor") String arPlacement,
        @Size(max = 2000) String description,
        @Size(max = 8) List<@Size(max = 30) String> dietaryTags,
        Boolean available,
        Long categoryId
) {
    public PresignRequestDto(String filename, String contentType, String dishTitle, BigDecimal priceTag,
                             DishCategory dishCategory, MediaType mediaType, Long originalSizeBytes,
                             Long uploadSizeBytes, String arPlacement, String description,
                             List<String> dietaryTags, Boolean available) {
        this(filename, contentType, dishTitle, priceTag, dishCategory, mediaType, originalSizeBytes,
                uploadSizeBytes, arPlacement, description, dietaryTags, available, null);
    }
}
