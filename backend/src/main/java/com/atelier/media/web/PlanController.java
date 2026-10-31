package com.atelier.media.web;

import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.service.AdminAccess;
import com.atelier.media.service.EntitlementService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class PlanController {
    private final EntitlementService service; private final AdminAccess access;
    public PlanController(EntitlementService service,AdminAccess access){this.service=service;this.access=access;}
    @ModelAttribute public void authorize(@AuthenticationPrincipal Jwt jwt){access.requireAccount(AdminPrincipal.from(jwt));}
    @GetMapping("/features") public ResponseEntity<?> registry(){return response(service.registry());}
    @GetMapping("/plans") public ResponseEntity<?> plans(){return response(service.plans());}
    @PostMapping("/plans") public ResponseEntity<?> create(@RequestBody EntitlementService.PlanWrite dto,@AuthenticationPrincipal Jwt jwt){return response(service.createPlan(dto,AdminPrincipal.from(jwt)));}
    @PutMapping("/plans/{id}") public ResponseEntity<?> revise(@PathVariable long id,@RequestBody EntitlementService.PlanWrite dto,@AuthenticationPrincipal Jwt jwt){return response(service.revisePlan(id,dto,AdminPrincipal.from(jwt)));}
    @GetMapping("/plans/{id}/revisions") public ResponseEntity<?> revisions(@PathVariable long id){return response(service.revisions(id));}
    @GetMapping("/restaurants/{id}/entitlements") public ResponseEntity<?> effective(@PathVariable long id){return response(service.effective(id,true));}
    @PostMapping("/restaurants/{id}/entitlements/assignment") public ResponseEntity<?> assign(@PathVariable long id,@RequestBody EntitlementService.AssignmentWrite dto,@AuthenticationPrincipal Jwt jwt){return response(service.assign(id,dto,AdminPrincipal.from(jwt)));}
    @PutMapping("/restaurants/{id}/entitlements/overrides/{key}") public ResponseEntity<?> override(@PathVariable long id,@PathVariable String key,@RequestBody EntitlementService.OverrideWrite dto,@AuthenticationPrincipal Jwt jwt){return response(service.override(id,key,dto,AdminPrincipal.from(jwt)));}
    public record OverrideRemoval(long expectedVersion,String reason) {}
    @DeleteMapping("/restaurants/{id}/entitlements/overrides/{key}") public ResponseEntity<?> remove(@PathVariable long id,@PathVariable String key,@RequestBody OverrideRemoval dto,@AuthenticationPrincipal Jwt jwt){return response(service.removeOverride(id,key,dto.expectedVersion(),dto.reason(),AdminPrincipal.from(jwt)));}
    private static ResponseEntity<?> response(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
