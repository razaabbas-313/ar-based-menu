package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Brand & menu settings. Empty strings clear optional fields. Links must be http(s) so they can
 * never carry javascript: URLs onto the guest menu. Logo/banner files are uploaded separately;
 * set {@code removeLogo}/{@code removeBanner} to delete them.
 */
public record StudioSettingsUpdateDto(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 160) String tagline,
        @Size(max = 2000) String bio,
        @Size(max = 500) String openingHours,
        @Pattern(regexp = "^$|^\\+?[0-9 ()\\-]{6,30}$", message = "use digits, spaces, + ( ) -") String phoneNumber,
        @Pattern(regexp = "^$|^\\+?[0-9 ()\\-]{6,30}$", message = "use digits with country code, e.g. +92 300 1234567") String whatsappNumber,
        @Pattern(regexp = "^$|^@?[A-Za-z0-9._]{1,30}$", message = "use your Instagram handle, e.g. @bistro") String instagramHandle,
        @Pattern(regexp = "^$|^https?://[^\\s<>\"']{3,240}$", message = "must start with https://") String facebookUrl,
        @Pattern(regexp = "^$|^https?://[^\\s<>\"']{3,240}$", message = "must start with https://") String websiteUrl,
        @Pattern(regexp = "^$|^@?[A-Za-z0-9._]{1,30}$", message = "use your TikTok handle, e.g. @bistro") String tiktokHandle,
        @Pattern(regexp = "^$|^https://(www\\.google\\.[a-z.]{2,10}/maps|maps\\.google\\.[a-z.]{2,10}|maps\\.app\\.goo\\.gl|goo\\.gl/maps|maps\\.apple\\.com)[^\\s<>\"']{0,450}$",
                message = "paste a Google Maps or Apple Maps share link (https://maps.app.goo.gl/...)") String googleMapsUrl,
        @Pattern(regexp = "amber|emerald|gold|zinc") String themeAccent,
        @Pattern(regexp = "cards|compact") String menuLayout,
        @Pattern(regexp = "^[A-Z]{3}$", message = "use a 3-letter currency code, e.g. USD") String currencyCode,
        @Size(max = 10) String currencySymbol,
        Boolean removeLogo,
        Boolean removeBanner
) {
}
