package com.atelier.media.dto;

import java.util.List;

/** One restaurant's public menu: branding + custom categories + in-stock, completed dishes. */
public record PublicMenuDto(
        RestaurantBrandingDto restaurant,
        List<MediaResponseDto> items,
        List<CategoryDto> categories,
        boolean menuLive
) {
    public PublicMenuDto(RestaurantBrandingDto restaurant, List<MediaResponseDto> items) {
        this(restaurant, items, List.of(), true);
    }
}
