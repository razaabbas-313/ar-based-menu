package com.atelier.media.web;

import com.atelier.media.security.Tenant;
import com.atelier.media.service.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/studio/entitlements")
public class OwnerEntitlementController {
    private final EntitlementService service;private final RestaurantAccess access;
    public OwnerEntitlementController(EntitlementService service,RestaurantAccess access){this.service=service;this.access=access;}
    @GetMapping public ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt){Tenant tenant=Tenant.from(jwt);access.requireStudioAccess(tenant);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.effective(tenant.restaurantId(),false));}
}
