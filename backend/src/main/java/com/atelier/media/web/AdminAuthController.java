package com.atelier.media.web;

import com.atelier.media.dto.LoginRequestDto;
import com.atelier.media.dto.admin.AdminAuthResponseDto;
import com.atelier.media.dto.admin.AdminProfileDto;
import com.atelier.media.dto.admin.ChangePasswordDto;
import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import com.atelier.media.security.ClientIpResolver;

/** Super-admin sign-in (public) and own-account endpoints (ROLE_SUPER_ADMIN). */
@RestController
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final ClientIpResolver clientIpResolver;

    public AdminAuthController(AdminAuthService adminAuthService, ClientIpResolver clientIpResolver) {
        this.adminAuthService = adminAuthService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/api/v1/auth/admin/login")
    public ResponseEntity<AdminAuthResponseDto> login(HttpServletRequest httpRequest, @Valid @RequestBody LoginRequestDto request) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(adminAuthService.login(request, clientIp));
    }

    @GetMapping("/api/v1/admin/me")
    public ResponseEntity<AdminProfileDto> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(adminAuthService.me(AdminPrincipal.from(jwt)));
    }

    @PutMapping("/api/v1/admin/me/password")
    public ResponseEntity<AdminProfileDto> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody ChangePasswordDto dto) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(adminAuthService.changePassword(AdminPrincipal.from(jwt), dto));
    }
}
