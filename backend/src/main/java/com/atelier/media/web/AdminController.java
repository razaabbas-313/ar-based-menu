package com.atelier.media.web;

import com.atelier.media.dto.Attach3dRequestDto;
import com.atelier.media.dto.AuthResponseDto;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.SlugAvailabilityDto;
import com.atelier.media.dto.admin.AdminModelApproveDto;
import com.atelier.media.dto.admin.AdminModelRequestDto;
import com.atelier.media.dto.admin.AdminNotesUpdateDto;
import com.atelier.media.dto.admin.AdminRestaurantCreateDto;
import com.atelier.media.dto.admin.AdminRestaurantCreatedDto;
import com.atelier.media.dto.admin.AdminRestaurantDetailDto;
import com.atelier.media.dto.admin.AdminRestaurantDto;
import com.atelier.media.dto.admin.AdminRestaurantUpdateDto;
import com.atelier.media.dto.admin.PageDto;
import com.atelier.media.dto.admin.PlatformAnalyticsDto;
import com.atelier.media.dto.admin.RestaurantStatusUpdateDto;
import com.atelier.media.dto.admin.TemporaryPasswordDto;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.service.AdminService;
import com.atelier.media.service.AdminAccess;
import com.atelier.media.service.ModelRequestService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.atelier.media.dto.PublicMenuDto;
import com.atelier.media.service.PublicMenuService;
import java.util.List;

/** SaaS platform owner console (ROLE_SUPER_ADMIN only, enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminAccess adminAccess;
    private final ModelRequestService modelRequestService;
    private final PublicMenuService publicMenuService;

    public AdminController(AdminService adminService, AdminAccess adminAccess,
                           ModelRequestService modelRequestService,
                           PublicMenuService publicMenuService) {
        this.adminService = adminService;
        this.adminAccess = adminAccess;
        this.modelRequestService = modelRequestService;
        this.publicMenuService = publicMenuService;
    }

    /** Applies to every handler, including reads and model-publication operations. */
    @ModelAttribute
    public void requireAdminAccess(@AuthenticationPrincipal Jwt jwt) {
        adminAccess.requireAccount(AdminPrincipal.from(jwt));
    }

    @GetMapping("/restaurants/{id}/menu-preview")
    public ResponseEntity<PublicMenuDto> restaurantMenuPreview(@PathVariable long id) {
        return noStore(publicMenuService.getStaffPreview(id));
    }

    @GetMapping("/analytics")
    public ResponseEntity<PlatformAnalyticsDto> analytics() {
        return noStore(adminService.analytics());
    }

    @GetMapping("/restaurants")
    public ResponseEntity<PageDto<AdminRestaurantDto>> restaurants(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RestaurantStatus status,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return noStore(adminService.list(q, status, includeDeleted, page, size, sort, "asc".equalsIgnoreCase(direction)));
    }

    @PostMapping("/restaurants")
    public ResponseEntity<AdminRestaurantCreatedDto> create(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody AdminRestaurantCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(adminService.create(dto, AdminPrincipal.from(jwt)));
    }

    @GetMapping("/restaurants/{id}")
    public ResponseEntity<AdminRestaurantDetailDto> detail(@PathVariable long id) {
        return noStore(adminService.detail(id));
    }

    @PutMapping("/restaurants/{id}")
    public ResponseEntity<AdminRestaurantDto> update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                     @Valid @RequestBody AdminRestaurantUpdateDto dto) {
        return noStore(adminService.update(id, dto, AdminPrincipal.from(jwt)));
    }

    @PutMapping("/restaurants/{id}/notes")
    public ResponseEntity<AdminRestaurantDto> updateNotes(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                          @RequestBody AdminNotesUpdateDto dto) {
        return noStore(adminService.updateNotes(id, dto.notes(), AdminPrincipal.from(jwt)));
    }

    @PutMapping("/restaurants/{id}/permission-3d")
    public ResponseEntity<AdminRestaurantDto> update3dPermission(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                                 @RequestBody com.atelier.media.dto.admin.AdminPermission3dDto dto) {
        return noStore(adminService.update3dPermission(id, Boolean.TRUE.equals(dto.canUpload3d()), AdminPrincipal.from(jwt)));
    }

    @PutMapping("/restaurants/{id}/status")
    public ResponseEntity<AdminRestaurantDto> status(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                     @Valid @RequestBody RestaurantStatusUpdateDto dto) {
        return noStore(adminService.updateStatus(id, dto, AdminPrincipal.from(jwt)));
    }

    /** Soft delete by default; {@code ?hard=true} removes the rows and wipes S3 asynchronously. */
    @DeleteMapping("/restaurants/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                    @RequestParam(defaultValue = "false") boolean hard) {
        AdminPrincipal admin = AdminPrincipal.from(jwt);
        if (hard) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(adminService.hardDelete(id, admin));
        }
        return noStore(adminService.softDelete(id, admin));
    }

    @PostMapping("/restaurants/{id}/restore")
    public ResponseEntity<AdminRestaurantDto> restore(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return noStore(adminService.restore(id, AdminPrincipal.from(jwt)));
    }

    @PostMapping("/restaurants/{id}/recalculate-usage")
    public ResponseEntity<AdminRestaurantDto> recalculate(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return noStore(adminService.recalculateUsage(id, AdminPrincipal.from(jwt)));
    }

    /** Issues a new temporary password; the owner must replace it at the next sign-in. */
    @PostMapping("/restaurants/{id}/reset-password")
    public ResponseEntity<TemporaryPasswordDto> resetPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return noStore(adminService.resetOwnerPassword(id, AdminPrincipal.from(jwt)));
    }

    /** Live slug check for the "New Restaurant" and edit forms. */
    @GetMapping("/slug-available")
    public ResponseEntity<SlugAvailabilityDto> slugAvailable(@RequestParam String slug,
                                                             @RequestParam(required = false) Long excludeId) {
        return noStore(adminService.slugAvailability(slug, excludeId));
    }

    /** One-click "open studio as owner": a short-lived, audited restaurant session. */
    @PostMapping("/restaurants/{id}/impersonate")
    public ResponseEntity<AuthResponseDto> impersonate(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return noStore(adminService.impersonate(id, AdminPrincipal.from(jwt)));
    }

    // ---------------------------------------------------------------- 3D Studio Pipeline

    @GetMapping("/3d-requests")
    public ResponseEntity<List<AdminModelRequestDto>> get3dRequests() {
        return noStore(modelRequestService.getPendingRequests());
    }

    @GetMapping("/3d-requests/history")
    public ResponseEntity<PageDto<com.atelier.media.dto.ModelRequestHistoryDto>> getAll3dRequestsHistory(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return noStore(modelRequestService.getAllHistory(restaurantId, status, page, size));
    }

    @GetMapping("/restaurants/{id}/model-requests")
    public ResponseEntity<List<com.atelier.media.dto.ModelRequestHistoryDto>> getRestaurantModelRequests(@PathVariable long id) {
        return noStore(modelRequestService.getHistoryForRestaurant(id));
    }

    @PostMapping("/3d-requests/{dishId}/presign")
    public ResponseEntity<PresignResponseDto> presignFinishedModel(
            @PathVariable long dishId,
            @Valid @RequestBody Attach3dRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(modelRequestService.presignModelUpload(dishId, request));
    }

    @PostMapping("/3d-requests/{dishId}/approve")
    public ResponseEntity<MediaResponseDto> approve3dModel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long dishId,
            @Valid @RequestBody AdminModelApproveDto dto) {
        return noStore(modelRequestService.approveModel(dishId, dto, AdminPrincipal.from(jwt)));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
