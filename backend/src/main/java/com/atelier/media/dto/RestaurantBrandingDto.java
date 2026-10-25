package com.atelier.media.dto;

import com.atelier.media.model.Restaurant;

/** Public branding shown on the guest menu. Never contains owner email or internal counters. */
public record RestaurantBrandingDto(
        Long id,
        String name,
        String slug,
        String tagline,
        String bio,
        String openingHours,
        String logoUrl,
        String bannerUrl,
        String phoneNumber,
        String whatsappNumber,
        String instagramHandle,
        String facebookUrl,
        String websiteUrl,
        String tiktokHandle,
        String googleMapsUrl,
        String themeAccent,
        String menuLayout,
        String currencyCode,
        String currencySymbol,
        boolean isMenuLive
) {
    public RestaurantBrandingDto(Long id, String name, String slug, String tagline, String bio,
                                 String openingHours, String logoUrl, String bannerUrl, String phoneNumber,
                                 String whatsappNumber, String instagramHandle, String facebookUrl,
                                 String websiteUrl, String tiktokHandle, String googleMapsUrl,
                                 String themeAccent, String menuLayout, String currencyCode,
                                 String currencySymbol) {
        this(id, name, slug, tagline, bio, openingHours, logoUrl, bannerUrl, phoneNumber,
                whatsappNumber, instagramHandle, facebookUrl, websiteUrl, tiktokHandle,
                googleMapsUrl, themeAccent, menuLayout, currencyCode, currencySymbol, true);
    }

    public static RestaurantBrandingDto from(Restaurant r) {
        return new RestaurantBrandingDto(r.getId(), r.getName(), r.getSlug(), r.getTagline(), r.getBio(),
                r.getOpeningHours(), r.getLogoUrl(), r.getBannerUrl(), r.getPhoneNumber(), r.getWhatsappNumber(),
                r.getInstagramHandle(), r.getFacebookUrl(), r.getWebsiteUrl(), r.getTiktokHandle(), r.getGoogleMapsUrl(),
                r.getThemeAccent(), r.getMenuLayout(), r.getCurrencyCode(), r.getCurrencySymbol(),
                !Boolean.FALSE.equals(r.getIsMenuLive()));
    }
}
