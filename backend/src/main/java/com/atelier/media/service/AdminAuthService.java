package com.atelier.media.service;

import com.atelier.media.dto.LoginRequestDto;
import com.atelier.media.dto.admin.AdminAuthResponseDto;
import com.atelier.media.dto.admin.AdminProfileDto;
import com.atelier.media.dto.admin.ChangePasswordDto;
import com.atelier.media.exception.InvalidCredentialsException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.InvalidTokenException;
import com.atelier.media.model.PlatformUser;
import com.atelier.media.repository.PlatformUserRepository;
import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.security.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Super-admin sign-in and password management (separate from restaurant accounts). */
@Slf4j
@Service
public class AdminAuthService {

    private final PlatformUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final com.atelier.media.security.LoginThrottle loginAttempts;
    private final String timingDummyHash;

    public AdminAuthService(PlatformUserRepository users, PasswordEncoder passwordEncoder,
                            TokenService tokenService, com.atelier.media.security.LoginThrottle loginAttempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
        this.timingDummyHash = passwordEncoder.encode("atelier-admin-timing-equalizer");
    }

    @Transactional
    public AdminAuthResponseDto login(LoginRequestDto request) {
        return login(request, "unknown");
    }

    @Transactional
    public AdminAuthResponseDto login(LoginRequestDto request, String clientIp) {
        String email = AuthService.normalizeEmail(request.email());
        String throttleKey = "admin:" + email;
        loginAttempts.checkAllowed(throttleKey, clientIp);

        Optional<PlatformUser> found = users.findByEmail(email).filter(PlatformUser::hasUsablePassword);
        String hash = found.map(PlatformUser::getPasswordHash).orElse(timingDummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !matches) {
            loginAttempts.recordFailure(throttleKey, clientIp);
            throw new InvalidCredentialsException();
        }
        loginAttempts.recordSuccess(throttleKey, clientIp);
        PlatformUser admin = found.get();
        admin.setLastLoginAt(Instant.now());
        users.save(admin);
        TokenService.IssuedToken token = tokenService.issueAdmin(admin);
        log.info("Super admin signed in: {}", admin.getEmail());
        return new AdminAuthResponseDto(token.value(), "Bearer", token.expiresAt(), token.expiresInSeconds(), AdminProfileDto.from(admin));
    }

    @Transactional(readOnly = true)
    public AdminProfileDto me(AdminPrincipal principal) {
        return AdminProfileDto.from(require(principal));
    }

    @Transactional
    public AdminProfileDto changePassword(AdminPrincipal principal, ChangePasswordDto dto) {
        PlatformUser admin = require(principal);
        if (!passwordEncoder.matches(dto.currentPassword(), admin.getPasswordHash())) {
            throw new InvalidFieldException("currentPassword", "Your current password is not correct.");
        }
        if (dto.currentPassword().equals(dto.newPassword())) {
            throw new InvalidFieldException("newPassword", "Choose a password you have not used here before.");
        }
        admin.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        admin.setMustChangePassword(false);
        admin.incrementTokenVersion();
        log.info("Super admin {} changed their password", admin.getEmail());
        return AdminProfileDto.from(users.save(admin));
    }

    private PlatformUser require(AdminPrincipal principal) {
        PlatformUser admin = users.findById(principal.adminId())
                .orElseThrow(() -> new InvalidTokenException("This admin account no longer exists. Please sign in again."));
        if (principal.tokenVersion() != null && admin.getTokenVersion() != principal.tokenVersion()) {
            throw new InvalidTokenException("This admin session has been revoked. Please sign in again.");
        }
        return admin;
    }
}
