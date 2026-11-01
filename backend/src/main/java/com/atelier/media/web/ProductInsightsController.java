package com.atelier.media.web;

import com.atelier.media.service.*;
import com.atelier.media.security.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@RestController
public class ProductInsightsController {
    private final GuestInsightService service;private final AdminAccess admins;private final ClientIpResolver ips;
    public ProductInsightsController(GuestInsightService service,AdminAccess admins,ClientIpResolver ips){this.service=service;this.admins=admins;this.ips=ips;}
    @PostMapping("/api/v1/public/insights/event") public ResponseEntity<?> record(@RequestBody GuestInsightService.Input dto,jakarta.servlet.http.HttpServletRequest request){service.record(dto,ips.resolveClientIp(request));return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @GetMapping("/api/v1/studio/insights") public ResponseEntity<?> report(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){var end=to==null?LocalDate.now(ZoneOffset.UTC):to;return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.report(Tenant.from(jwt),from==null?end.minusDays(29):from,end));}
    @GetMapping("/api/v1/admin/insights") public ResponseEntity<?> platform(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){admins.requireAccount(AdminPrincipal.from(jwt));var end=to==null?LocalDate.now(ZoneOffset.UTC):to;return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.platform(from==null?end.minusDays(29):from,end));}
}
