package com.atelier.media.dto;

import com.atelier.media.model.DishCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Full replacement of a dish's menu metadata (PUT semantics). Files are managed separately. */
public record DishUpdateDto(
        @NotBlank @Size(max = 160) String dishTitle,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal priceTag,
        DishCategory dishCategory,
        @Size(max = 2000) String description,
        @Size(max = 8) List<@Size(max = 30) String> dietaryTags,
        @NotNull Boolean available,
        @Pattern(regexp = "table|floor") String arPlacement,
        Boolean dailySpecial,
        Long categoryId
) {
    public DishUpdateDto(String dishTitle, BigDecimal priceTag, DishCategory dishCategory, String description,
                         List<String> dietaryTags, Boolean available, String arPlacement, Boolean dailySpecial) {
        this(dishTitle, priceTag, dishCategory, description, dietaryTags, available, arPlacement, dailySpecial, null);
    }
}
