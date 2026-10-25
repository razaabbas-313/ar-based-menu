package com.atelier.media.dto;

public record TopDishDto(
        Long id,
        String title,
        String imageUrl,
        long viewsCount,
        long modelViewsCount,
        long arLaunchesCount
) {
}
