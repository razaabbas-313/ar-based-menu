package com.atelier.media.web;

import com.atelier.media.security.*;
import com.atelier.media.service.*;
import com.atelier.media.repository.*;
import com.atelier.media.dto.*;
import com.atelier.media.dto.admin.AdminModelApproveDto;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class ProductStudioController {
    private final ProductStudioService studio;
    private final ModelHistoryService history;
    private final RestaurantAccess owners;
    private final AdminAccess admins;
    private final MediaRepository media;
    private final ModelRequestService models;
    public ProductStudioController(ProductStudioService studio,ModelHistoryService history,RestaurantAccess owners,AdminAccess admins,MediaRepository media,ModelRequestService models){this.studio=studio;this.history=history;this.owners=owners;this.admins=admins;this.media=media;this.models=models;}
    public record Draft(long expectedVersion,Map<String,String> values){}
    public record Version(long expectedVersion){}
    public record Review(long expectedVersion,String url,boolean enabled){}
    @GetMapping("/api/v1/studio/appearance") public ResponseEntity<?> appearance(@AuthenticationPrincipal Jwt jwt){return ok(studio.appearance(Tenant.from(jwt)));}
    @PutMapping("/api/v1/studio/appearance/draft") public ResponseEntity<?> draft(@AuthenticationPrincipal Jwt jwt,@RequestBody Draft dto){return ok(studio.saveDraft(Tenant.from(jwt),dto.expectedVersion(),dto.values()));}
    @PostMapping("/api/v1/studio/appearance/publish") public ResponseEntity<?> publish(@AuthenticationPrincipal Jwt jwt,@RequestBody Version dto){return ok(studio.publish(Tenant.from(jwt),dto.expectedVersion()));}
    @PostMapping("/api/v1/studio/appearance/discard") public ResponseEntity<?> discard(@AuthenticationPrincipal Jwt jwt,@RequestBody Version dto){return ok(studio.discard(Tenant.from(jwt),dto.expectedVersion()));}
    @GetMapping("/api/v1/studio/reviews") public ResponseEntity<?> reviews(@AuthenticationPrincipal Jwt jwt){return ok(studio.reviewSettings(Tenant.from(jwt)));}
    @PutMapping("/api/v1/studio/reviews") public ResponseEntity<?> saveReview(@AuthenticationPrincipal Jwt jwt,@RequestBody Review dto){return ok(studio.saveReview(Tenant.from(jwt),dto.expectedVersion(),dto.url(),dto.enabled()));}
    @GetMapping("/api/v1/public/appearance/{slug}") public ResponseEntity<?> publicView(@PathVariable String slug){return ok(studio.publicAppearance(PublicMenuController.normalize(slug)));}
    @GetMapping("/api/v1/studio/dishes/{dishId}/model-history") public ResponseEntity<?> ownerHistory(@AuthenticationPrincipal Jwt jwt,@PathVariable long dishId,@RequestParam(defaultValue="0") long after){var t=Tenant.from(jwt);owners.requireStudioAccess(t);owned(t.restaurantId(),dishId);return ok(history.list(t.restaurantId(),dishId,after));}
    @GetMapping("/api/v1/admin/restaurants/{restaurantId}/dishes/{dishId}/model-history") public ResponseEntity<?> adminHistory(@AuthenticationPrincipal Jwt jwt,@PathVariable long restaurantId,@PathVariable long dishId,@RequestParam(defaultValue="0") long after){admins.requireAccount(AdminPrincipal.from(jwt));owned(restaurantId,dishId);return ok(history.list(restaurantId,dishId,after));}
    @PostMapping("/api/v1/admin/restaurants/{restaurantId}/dishes/{dishId}/model/presign") public ResponseEntity<?> adminPresign(@AuthenticationPrincipal Jwt jwt,@PathVariable long restaurantId,@PathVariable long dishId,@jakarta.validation.Valid @RequestBody Attach3dRequestDto dto){admins.requireAccount(AdminPrincipal.from(jwt));owned(restaurantId,dishId);return ok(models.presignModelUpload(dishId,dto,AdminPrincipal.from(jwt)));}
    @PostMapping("/api/v1/admin/restaurants/{restaurantId}/dishes/{dishId}/model/confirm") public ResponseEntity<?> adminConfirm(@AuthenticationPrincipal Jwt jwt,@PathVariable long restaurantId,@PathVariable long dishId,@jakarta.validation.Valid @RequestBody AdminModelApproveDto dto){admins.requireAccount(AdminPrincipal.from(jwt));owned(restaurantId,dishId);return ok(models.approveModel(dishId,dto,AdminPrincipal.from(jwt)));}
    @GetMapping("/api/v1/public/brand-assets/{id}") public ResponseEntity<?> image(@PathVariable String id){return ResponseEntity.status(302).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").location(java.net.URI.create(studio.publicAssetUrl(id))).build();}
    private void owned(long restaurant,long dish){media.findByIdAndRestaurantId(dish,restaurant).orElseThrow(()->new com.atelier.media.exception.InvalidFieldException("dishId","Dish not found in the selected restaurant."));}
    private static ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
