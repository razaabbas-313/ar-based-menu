package com.atelier.media.web;

import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.PublicMenuDto;
import com.atelier.media.model.DishCategory;
import com.atelier.media.service.PublicMenuService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * The v2 menu route, kept so printed QR codes keep working. There is deliberately no directory:
 * menus are private and reachable only through their direct /r/{slug} link.
 */
@RestController
@RequestMapping("/api/v1/restaurants")
public class RestaurantController {

    private final PublicMenuService publicMenuService;

    public RestaurantController(PublicMenuService publicMenuService) {
        this.publicMenuService = publicMenuService;
    }

    /** Same data as /api/v1/public/menu/{slug}, with optional server-side filters. */
    @GetMapping("/{slug}/menu")
    public ResponseEntity<PublicMenuDto> menu(@PathVariable String slug,
                                              @RequestParam(required = false) DishCategory category,
                                              @RequestParam(defaultValue = "false") boolean arOnly) {
        PublicMenuDto menu = publicMenuService.getMenu(PublicMenuController.normalize(slug));
        List<MediaResponseDto> items = menu.items().stream()
                .filter(m -> category == null || m.dishCategory() == category)
                .filter(m -> !arOnly || m.hasArSupport())
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PublicMenuDto(menu.restaurant(), items));
    }
}
