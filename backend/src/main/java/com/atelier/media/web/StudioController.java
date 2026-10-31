package com.atelier.media.web;

import com.atelier.media.dto.PublicMenuDto;
import com.atelier.media.service.PublicMenuService;
import com.atelier.media.dto.Attach3dRequestDto;
import com.atelier.media.dto.AvailabilityDto;
import com.atelier.media.dto.BrandingAssetConfirmDto;
import com.atelier.media.dto.BrandingAssetPresignDto;
import com.atelier.media.dto.CategoryCreateDto;
import com.atelier.media.dto.CategoryDto;
import com.atelier.media.dto.CategoryReorderDto;
import com.atelier.media.dto.CategoryUpdateDto;
import com.atelier.media.dto.DishUpdateDto;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.MenuStatusUpdateDto;
import com.atelier.media.dto.ModelConfirmDto;
import com.atelier.media.dto.ModelRequestConfirmDto;
import com.atelier.media.dto.ModelRequestItemDto;
import com.atelier.media.dto.ModelRequestPresignResponseDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.StudioAnalyticsDto;
import com.atelier.media.dto.StudioSettingsDto;
import com.atelier.media.dto.StudioSettingsUpdateDto;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import com.atelier.media.service.AnalyticsService;
import com.atelier.media.service.BrandingService;
import com.atelier.media.service.CategoryService;
import com.atelier.media.service.DishService;
import com.atelier.media.service.MenuCache;
import com.atelier.media.service.ModelRequestService;
import com.atelier.media.service.RestaurantAccess;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Restaurant Admin studio (ROLE_RESTAURANT). The tenant always comes from the JWT. */
@RestController
@RequestMapping("/api/v1/studio")
public class StudioController {

    private final DishService dishService;
    private final RestaurantAccess restaurantAccess;
    private final BrandingService brandingService;
    private final CategoryService categoryService;
    private final ModelRequestService modelRequestService;
    private final AnalyticsService analyticsService;
    private final RestaurantRepository restaurantRepository;
    private final MenuCache menuCache;
    private final PublicMenuService publicMenuService;

    public StudioController(DishService dishService, RestaurantAccess restaurantAccess,
                            BrandingService brandingService,
                            CategoryService categoryService,
                            ModelRequestService modelRequestService,
                            AnalyticsService analyticsService,
                            RestaurantRepository restaurantRepository,
                            MenuCache menuCache,
                            PublicMenuService publicMenuService) {
        this.dishService = dishService;
        this.restaurantAccess = restaurantAccess;
        this.brandingService = brandingService;
        this.categoryService = categoryService;
        this.modelRequestService = modelRequestService;
        this.analyticsService = analyticsService;
        this.restaurantRepository = restaurantRepository;
        this.menuCache = menuCache;
        this.publicMenuService = publicMenuService;
    }

    /** Fail closed for all studio handlers, including those delegating by restaurant ID. */
    @ModelAttribute
    public void requireStudioAccess(@AuthenticationPrincipal Jwt jwt) {
        restaurantAccess.requireStudioAccess(Tenant.from(jwt));
    }

    @GetMapping("/menu-preview")
    public ResponseEntity<PublicMenuDto> menuPreview(@AuthenticationPrincipal Jwt jwt) {
        Tenant tenant = Tenant.from(jwt);
        return noStore(publicMenuService.getStaffPreview(tenant.restaurantId()));
    }

    // ---------------------------------------------------------------- dishes

    @GetMapping("/dishes")
    public ResponseEntity<List<MediaResponseDto>> dishes(@AuthenticationPrincipal Jwt jwt) {
        return noStore(dishService.listDishes(Tenant.from(jwt)));
    }

    @PutMapping("/dishes/{id}")
    public ResponseEntity<MediaResponseDto> updateDish(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                       @Valid @RequestBody DishUpdateDto update) {
        return noStore(dishService.updateDish(Tenant.from(jwt), id, update));
    }

    @PatchMapping("/dishes/{id}/availability")
    public ResponseEntity<MediaResponseDto> availability(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                         @Valid @RequestBody AvailabilityDto body) {
        return noStore(dishService.setAvailability(Tenant.from(jwt), id, body.available()));
    }

    @DeleteMapping("/dishes/{id}")
    public ResponseEntity<Void> deleteDish(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        dishService.deleteDish(Tenant.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- categories

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategories(@AuthenticationPrincipal Jwt jwt) {
        Tenant tenant = Tenant.from(jwt);
        return noStore(categoryService.getCategories(tenant.restaurantId()));
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryDto> createCategory(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody CategoryCreateDto dto) {
        Tenant tenant = Tenant.from(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(tenant.restaurantId(), dto));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryDto> updateCategory(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody CategoryUpdateDto dto) {
        Tenant tenant = Tenant.from(jwt);
        return noStore(categoryService.updateCategory(tenant.restaurantId(), id, dto));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Tenant tenant = Tenant.from(jwt);
        categoryService.deleteCategory(tenant.restaurantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/categories/reorder")
    public ResponseEntity<List<CategoryDto>> reorderCategories(@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody CategoryReorderDto dto) {
        Tenant tenant = Tenant.from(jwt);
        return noStore(categoryService.reorderCategories(tenant.restaurantId(), dto.orderedIds()));
    }

    // ---------------------------------------------------------------- menu status switch

    @PutMapping("/menu-status")
    public ResponseEntity<MenuStatusUpdateDto> updateMenuStatus(@AuthenticationPrincipal Jwt jwt,
                                                                @Valid @RequestBody MenuStatusUpdateDto dto) {
        Tenant tenant = Tenant.from(jwt);
        Restaurant r = restaurantRepository.findById(tenant.restaurantId())
                .orElseThrow(() -> new RestaurantNotFoundException(tenant.restaurantId()));
        r.setIsMenuLive(dto.isMenuLive());
        restaurantRepository.save(r);
        menuCache.evict(r.getSlug());
        return noStore(new MenuStatusUpdateDto(r.getIsMenuLive()));
    }

    // ---------------------------------------------------------------- 3D concierge pipeline

    @PostMapping("/dishes/{id}/model-request/presign")
    public ResponseEntity<List<ModelRequestPresignResponseDto>> presignModelRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody List<ModelRequestItemDto> items) {
        Tenant tenant = Tenant.from(jwt);
        return noStore(modelRequestService.presignPhotos(tenant.restaurantId(), id, items));
    }

    @PostMapping("/dishes/{id}/model-request/confirm")
    public ResponseEntity<MediaResponseDto> confirmModelRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody ModelRequestConfirmDto dto) {
        Tenant tenant = Tenant.from(jwt);
        return noStore(modelRequestService.confirmRequest(tenant.restaurantId(), id, dto));
    }

    @GetMapping("/model-requests")
    public ResponseEntity<List<com.atelier.media.dto.ModelRequestHistoryDto>> getStudioModelRequests(@AuthenticationPrincipal Jwt jwt) {
        Tenant tenant = Tenant.from(jwt);
        return noStore(modelRequestService.getHistoryForRestaurant(tenant.restaurantId()));
    }

    // ---------------------------------------------------------------- analytics

    @GetMapping("/analytics")
    public ResponseEntity<StudioAnalyticsDto> analytics(@AuthenticationPrincipal Jwt jwt) {
        Tenant tenant = Tenant.from(jwt);
        return noStore(analyticsService.getStudioAnalytics(tenant.restaurantId()));
    }

    // ---------------------------------------------------------------- legacy 3D track (kept for backward compatibility)

    @PostMapping("/dishes/{id}/attach-3d")
    public ResponseEntity<PresignResponseDto> attach3d(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                       @Valid @RequestBody Attach3dRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(dishService.requestModelUpload(Tenant.from(jwt), id, request));
    }

    @PostMapping("/dishes/{id}/confirm-3d")
    public ResponseEntity<MediaResponseDto> confirm3d(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                      @Valid @RequestBody ModelConfirmDto confirm) {
        return noStore(dishService.confirmModelUpload(Tenant.from(jwt), id, confirm));
    }

    @DeleteMapping("/dishes/{id}/model")
    public ResponseEntity<MediaResponseDto> removeModel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return noStore(dishService.removeModel(Tenant.from(jwt), id));
    }

    // ---------------------------------------------------------------- branding

    @GetMapping("/settings")
    public ResponseEntity<StudioSettingsDto> settings(@AuthenticationPrincipal Jwt jwt) {
        return noStore(brandingService.getSettings(Tenant.from(jwt)));
    }

    @PutMapping("/settings")
    public ResponseEntity<StudioSettingsDto> updateSettings(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody StudioSettingsUpdateDto update) {
        return noStore(brandingService.updateSettings(Tenant.from(jwt), update));
    }

    @PostMapping("/branding/{kind}/presign")
    public ResponseEntity<PresignResponseDto> presignBranding(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind,
                                                              @Valid @RequestBody BrandingAssetPresignDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(brandingService.presignAsset(Tenant.from(jwt), kind, request));
    }

    @PostMapping("/branding/{kind}/confirm")
    public ResponseEntity<StudioSettingsDto> confirmBranding(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind,
                                                             @Valid @RequestBody BrandingAssetConfirmDto confirm) {
        return noStore(brandingService.confirmAsset(Tenant.from(jwt), kind, confirm));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
