package com.atelier.media.dto.admin;

import java.time.Instant;
import java.util.List;

public record PlatformAnalyticsDto(
        long totalRestaurants,
        long activeRestaurants,
        long trialRestaurants,
        long pendingRestaurants,
        long suspendedRestaurants,
        long totalDishes,
        long total3dModels,
        long totalStorageBytes,
        List<AdminRestaurantDto> topByStorage,
        List<AdminRestaurantDto> topByDishes,
        Instant generatedAt,
        long modelsInProduction,
        long modelsPublished,
        long totalArLaunches,
        long totalMenuViews
) {
    public PlatformAnalyticsDto(long totalRestaurants, long activeRestaurants, long trialRestaurants,
                                long pendingRestaurants, long suspendedRestaurants, long totalDishes,
                                long total3dModels, long totalStorageBytes, List<AdminRestaurantDto> topByStorage,
                                List<AdminRestaurantDto> topByDishes, Instant generatedAt) {
        this(totalRestaurants, activeRestaurants, trialRestaurants, pendingRestaurants, suspendedRestaurants,
                totalDishes, total3dModels, totalStorageBytes, topByStorage, topByDishes, generatedAt,
                0L, total3dModels, 0L, 0L);
    }
}
