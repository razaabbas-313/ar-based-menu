package com.atelier.media.web;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.AuthResponseDto;
import com.atelier.media.dto.ChangeOwnPasswordDto;
import com.atelier.media.dto.ForgotPasswordDto;
import com.atelier.media.dto.LoginRequestDto;
import com.atelier.media.dto.MessageResponseDto;
import com.atelier.media.dto.ResetPasswordOtpDto;
import com.atelier.media.dto.RestaurantDto;
import com.atelier.media.security.ClientIpResolver;
import com.atelier.media.security.Tenant;
import com.atelier.media.service.AuthService;
import com.atelier.media.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Restaurant staff authentication.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final ClientIpResolver clientIpResolver;
    private final AtelierProperties props;

    public AuthController(AuthService authService,
                          OtpService otpService,
                          ClientIpResolver clientIpResolver,
                          AtelierProperties props) {
        this.authService = authService;
        this.otpService = otpService;
        this.clientIpResolver = clientIpResolver;
        this.props = props;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("passwordResetEnabled", props.passwordReset().enabled()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(HttpServletRequest httpRequest, @Valid @RequestBody LoginRequestDto request) {
        String clientIp = clientIpResolver.resolveClientIp(httpRequest);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.login(request, clientIp));
    }

    @GetMapping("/me")
    public ResponseEntity<RestaurantDto> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.me(Tenant.from(jwt)));
    }

    /** Replaces the (temporary) password and returns a new full-access studio token. */
    @PostMapping("/change-password")
    public ResponseEntity<AuthResponseDto> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody ChangeOwnPasswordDto request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.changePassword(Tenant.from(jwt), request));
    }

    /** Self-service forgot password: generates 6-digit OTP and logs/sends it. */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponseDto> forgotPassword(
            HttpServletRequest request,
            @Valid @RequestBody ForgotPasswordDto dto) {
        if (!props.passwordReset().enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Password reset is not enabled.");
        }
        String clientIp = clientIpResolver.resolveClientIp(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(otpService.sendPasswordResetOtp(dto, clientIp));
    }

    /** Completes password reset with the 6-digit OTP. */
    @PostMapping("/reset-password-otp")
    public ResponseEntity<MessageResponseDto> resetPasswordOtp(@Valid @RequestBody ResetPasswordOtpDto request) {
        if (!props.passwordReset().enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Password reset is not enabled.");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(otpService.resetPasswordWithOtp(request));
    }
}
