package com.atelier.media.dto;

/** @param reason human-readable explanation when {@code available} is false */
public record SlugAvailabilityDto(String slug, boolean available, String reason) {
}
