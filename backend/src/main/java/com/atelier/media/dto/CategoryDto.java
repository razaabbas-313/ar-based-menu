package com.atelier.media.dto;

import com.atelier.media.model.Category;

public record CategoryDto(Long id, Long restaurantId, String name, int displayOrder, boolean isActive) {
    public static CategoryDto from(Category c) {
        return new CategoryDto(c.getId(), c.getRestaurantId(), c.getName(),
                c.getDisplayOrder() == null ? 0 : c.getDisplayOrder(),
                Boolean.TRUE.equals(c.getIsActive()));
    }
}
