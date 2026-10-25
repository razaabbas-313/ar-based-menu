package com.atelier.media.service;

import com.atelier.media.exception.InvalidFieldException;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The fixed dietary vocabulary shown as pills on the guest menu and used by its filter drawer. */
public final class DietaryTags {

    public static final List<String> ALLOWED = List.of(
            "vegan", "vegetarian", "halal", "gluten-free", "dairy-free", "nut-free", "spicy", "chef-pick");

    private DietaryTags() {
    }

    /** Lower-cases, de-duplicates and validates; throws a field error for unknown tags. */
    public static List<String> normalize(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String raw : tags) {
            if (raw == null || raw.isBlank()) continue;
            String tag = raw.strip().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
            if (!ALLOWED.contains(tag)) {
                throw new InvalidFieldException("dietaryTags", "Unknown dietary tag '" + raw + "'. Allowed: " + String.join(", ", ALLOWED));
            }
            out.add(tag);
        }
        return List.copyOf(out);
    }

    public static String toColumn(List<String> tags) {
        List<String> normalized = normalize(tags);
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    public static List<String> fromColumn(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        return Arrays.stream(column.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
