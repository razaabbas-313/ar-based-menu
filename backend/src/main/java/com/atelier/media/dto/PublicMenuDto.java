package com.atelier.media.dto;

import java.util.List;

/** One restaurant's public menu: branding + custom categories + in-stock, completed dishes. */
public record PublicMenuDto(
        RestaurantBrandingDto restaurant,
        List<MediaResponseDto> items,
        List<CategoryDto> categories,
        boolean menuLive,
        List<String> allowedModes,
        String menuMode
) {
    public PublicMenuDto(RestaurantBrandingDto restaurant,List<MediaResponseDto> items,List<CategoryDto> categories,boolean live,List<String> modes) {
        this(restaurant,items,categories,live,modes,modes.contains("AR")?"AR":modes.contains("THREE_D")?"THREE_D":"SIMPLE");
    }
    public PublicMenuDto(RestaurantBrandingDto restaurant,List<MediaResponseDto> items,List<CategoryDto> categories,boolean menuLive) {
        this(restaurant,items,categories,menuLive,List.of("SIMPLE","THREE_D","AR"));
    }
    public PublicMenuDto(RestaurantBrandingDto restaurant, List<MediaResponseDto> items) {
        this(restaurant, items, List.of(), true);
    }
}
