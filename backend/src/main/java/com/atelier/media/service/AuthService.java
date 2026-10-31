package com.atelier.media.service;

import com.atelier.media.dto.AuthResponseDto;
import com.atelier.media.dto.ChangeOwnPasswordDto;
import com.atelier.media.dto.LoginRequestDto;
import com.atelier.media.dto.RestaurantDto;
import com.atelier.media.exception.AccountSuspendedException;
import com.atelier.media.exception.InvalidCredentialsException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import com.atelier.media.security.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Staff sign-in, the current profile and password changes. There is no public sign-up: restaurants
 * are provisioned by the super admin with a temporary password ({@link AdminService#create}), and
 * the owner must replace it on first sign-in.
 */
@Slf4j
@Service
public class AuthService {

    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final com.atelier.media.security.LoginThrottle loginAttempts;
    private final RestaurantAccess restaurantAccess;
    private final TransactionTemplate tx;
    /** Compared against when the email is unknown, so response time does not reveal registered emails. */
    private final String timingDummyHash;

    public AuthService(RestaurantRepository restaurantRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       com.atelier.media.security.LoginThrottle loginAttempts,
                       RestaurantAccess restaurantAccess,
                       TransactionTemplate tx) {
        this.restaurantRepository = restaurantRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
        this.restaurantAccess = restaurantAccess;
        this.tx = tx;
        this.timingDummyHash = passwordEncoder.encode("atelier-timing-equalizer");
    }

    /**
     * Returns a full studio token, or, while {@code must_change_password} is set, a setup-only token
     * with {@code mustChangePassword = true} that can only call /me and /change-password.
     */
    public AuthResponseDto login(LoginRequestDto request) {
        return login(request, "unknown");
    }

    public AuthResponseDto login(LoginRequestDto request, String clientIp) {
        String email = normalizeEmail(request.email());
        loginAttempts.checkAllowed(email, clientIp);

        Optional<Restaurant> found = restaurantRepository.findByEmail(email).filter(r -> !r.isDeleted());
        String hash = found.map(Restaurant::getPasswordHash).orElse(timingDummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !matches) {
            loginAttempts.recordFailure(email, clientIp);
            throw new InvalidCredentialsException();
        }
        loginAttempts.recordSuccess(email, clientIp);
        if (!found.get().getStatus().canUseStudio()) {
            throw new AccountSuspendedException();
        }
        return authResponse(found.get());
    }

    public RestaurantDto me(Tenant tenant) {
        return RestaurantDto.from(restaurantAccess.requireAccount(tenant));
    }

    /**
     * Verifies the current password with BCrypt, stores the new hash, clears
     * {@code must_change_password} and returns a fresh full-access studio token.
     */
    public AuthResponseDto changePassword(Tenant tenant, ChangeOwnPasswordDto request) {
        Restaurant restaurant = restaurantAccess.requireAccount(tenant);
        if (tenant.impersonated()) {
            throw new InvalidFieldException("currentPassword",
                    "Support sessions cannot change the owner's password. Use \"Reset password\" in the admin console.");
        }
        String rateKey = restaurant.getEmail();
        loginAttempts.checkAllowed(rateKey);
        if (!passwordEncoder.matches(request.currentPassword(), restaurant.getPasswordHash())) {
            loginAttempts.recordFailure(rateKey);
            throw new InvalidFieldException("currentPassword", "The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), restaurant.getPasswordHash())) {
            throw new InvalidFieldException("newPassword", "Choose a password that is different from the temporary one.");
        }
        loginAttempts.recordSuccess(rateKey);
        String newHash = passwordEncoder.encode(request.newPassword());
        Restaurant saved = tx.execute(status -> {
            Restaurant r = restaurantRepository.findById(restaurant.getId()).orElse(restaurant);
            r.setPasswordHash(newHash);
            r.setMustChangePassword(false);
            r.setPasswordChangedAt(Instant.now());
            r.incrementTokenVersion();
            return restaurantRepository.save(r);
        });
        log.info("Restaurant {} ({}) set a new password", saved.getSlug(), saved.getId());
        return authResponse(saved);
    }

    // ------------------------------------------------------------------ helpers

    private AuthResponseDto authResponse(Restaurant restaurant) {
        TokenService.IssuedToken token = tokenService.issue(restaurant);
        return new AuthResponseDto(token.value(), "Bearer", token.expiresAt(), token.expiresInSeconds(),
                RestaurantDto.from(restaurant), null, restaurant.requiresPasswordChange());
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
