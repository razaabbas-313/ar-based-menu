package com.atelier.media.web;

import com.atelier.media.dto.AnalyticsEventDto;
import com.atelier.media.dto.PublicMenuDto;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.service.AnalyticsService;
import com.atelier.media.service.PublicMenuService;
import com.atelier.media.service.SlugPolicy;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Locale;

/** Guest read path (anonymous): branding + in-stock dishes, served from the per-slug cache. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicMenuController {

    private final PublicMenuService publicMenuService;
    private final AnalyticsService analyticsService;
    private final com.atelier.media.security.ClientIpResolver clientIpResolver;

    public PublicMenuController(PublicMenuService publicMenuService,
                                AnalyticsService analyticsService,
                                com.atelier.media.security.ClientIpResolver clientIpResolver) {
        this.publicMenuService = publicMenuService;
        this.analyticsService = analyticsService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/menu/{slug}")
    public ResponseEntity<PublicMenuDto> menu(@PathVariable String slug) {
        String cleanSlug = normalize(slug);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(publicMenuService.getMenu(cleanSlug));
    }

    @PostMapping("/analytics/event")
    public ResponseEntity<Void> recordAnalyticsEvent(jakarta.servlet.http.HttpServletRequest request,
                                                     @Valid @RequestBody AnalyticsEventDto event) {
        String clientIp = clientIpResolver.resolveClientIp(request);
        analyticsService.recordEvent(event, clientIp);
        return ResponseEntity.accepted().build();
    }

    static String normalize(String slug) {
        String normalized = slug.toLowerCase(Locale.ROOT);
        if (!SlugPolicy.isWellFormed(normalized)) {
            throw new RestaurantNotFoundException(slug);
        }
        return normalized;
    }
}
