package com.atelier.media.web;

import com.atelier.media.service.GuestExperienceService;
import com.atelier.media.security.Tenant;
import com.atelier.media.security.ClientIpResolver;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class GuestExperienceController {
    private final GuestExperienceService service;private final ClientIpResolver ips;
    public GuestExperienceController(GuestExperienceService service,ClientIpResolver ips){this.service=service;this.ips=ips;}
    public record TableCreate(String name) {} public record Version(long expectedVersion) {}
    public record Context(String tableToken) {}
    @GetMapping("/api/v1/studio/experience") public ResponseEntity<?> settings(@AuthenticationPrincipal Jwt jwt){return response(service.settings(Tenant.from(jwt)));}
    @PutMapping("/api/v1/studio/experience") public ResponseEntity<?> save(@AuthenticationPrincipal Jwt jwt,@RequestBody GuestExperienceService.SettingsWrite dto){return response(service.saveSettings(Tenant.from(jwt),dto));}
    @GetMapping("/api/v1/studio/tables") public ResponseEntity<?> tables(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") long after){return response(service.tables(Tenant.from(jwt),after));}
    @PostMapping("/api/v1/studio/tables") public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@RequestBody TableCreate dto){return response(service.createTable(Tenant.from(jwt),dto.name()));}
    @PutMapping("/api/v1/studio/tables/{id}") public ResponseEntity<?> update(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody GuestExperienceService.TableWrite dto){return response(service.updateTable(Tenant.from(jwt),id,dto));}
    @PostMapping("/api/v1/studio/tables/{id}/rotate") public ResponseEntity<?> rotate(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestBody Version dto){return response(service.rotateTable(Tenant.from(jwt),id,dto.expectedVersion()));}
    @PostMapping("/api/v1/public/context/{slug}") public ResponseEntity<?> context(@PathVariable String slug,@RequestBody Context dto,jakarta.servlet.http.HttpServletRequest req){service.checkRate("context",ips.resolveClientIp(req),60);return response(service.publicContext(PublicMenuController.normalize(slug),dto.tableToken()));}
    @PostMapping("/api/v1/public/order-preparations/{slug}") public ResponseEntity<?> prepare(@PathVariable String slug,@RequestBody GuestExperienceService.OrderPrepare dto,jakarta.servlet.http.HttpServletRequest req){return response(service.prepareOrder(PublicMenuController.normalize(slug),dto,ips.resolveClientIp(req)));}
    @PostMapping("/api/v1/public/waiter-requests/{slug}") public ResponseEntity<?> waiter(@PathVariable String slug,@RequestBody GuestExperienceService.WaiterWrite dto,jakarta.servlet.http.HttpServletRequest req){return response(service.waiter(PublicMenuController.normalize(slug),dto,ips.resolveClientIp(req)));}
    private static ResponseEntity<?> response(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
