package com.atelier.media.dto;

import java.util.List;

public record StudioAnalyticsDto(
        long totalMenuViews,
        long totalModelViews,
        long totalArLaunches,
        double engagement3dRatePercent,
        List<TopDishDto> topDishes
) {
}
