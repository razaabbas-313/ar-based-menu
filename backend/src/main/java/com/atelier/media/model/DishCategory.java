package com.atelier.media.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/** Menu sections, in the order guests see them. */
public enum DishCategory {
    STARTERS,
    MAINS,
    CHEF_SPECIALS,
    DESSERTS,
    DRINKS;

    @JsonCreator
    public static DishCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_').replace("'", "");
        for (DishCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(normalized)) {
                return cat;
            }
        }
        if (normalized.contains("CHEF")) {
            return CHEF_SPECIALS;
        }
        if (normalized.contains("START") || normalized.contains("APPETIZ")) {
            return STARTERS;
        }
        if (normalized.contains("DESSERT") || normalized.contains("SWEET")) {
            return DESSERTS;
        }
        if (normalized.contains("DRINK") || normalized.contains("BEVERAG")) {
            return DRINKS;
        }
        if (normalized.contains("MAIN") || normalized.contains("ENTREE")) {
            return MAINS;
        }
        return null;
    }
}

