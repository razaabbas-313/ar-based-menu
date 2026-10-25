package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.ForgotPasswordDto;
import com.atelier.media.dto.MessageResponseDto;
import com.atelier.media.dto.ResetPasswordOtpDto;
import com.atelier.media.exception.ConflictException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.model.PasswordResetOtp;
import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.PasswordResetOtpRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Service
public class OtpService {

    public static final String GENERIC_OTP_SENT_MESSAGE =
            "If an account exists for this email, a verification code has been sent.";

    private final PasswordResetOtpRepository otpRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final AtelierProperties props;
    private final SecureRandom random = new SecureRandom();

    private final Cache<String, Integer> ipOtpLimiter = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    public OtpService(PasswordResetOtpRepository otpRepository,
                      RestaurantRepository restaurantRepository,
                      PasswordEncoder passwordEncoder,
                      EmailSender emailSender,
                      AtelierProperties props) {
        this.otpRepository = otpRepository;
        this.restaurantRepository = restaurantRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.props = props;
    }

    @Transactional
    public MessageResponseDto sendPasswordResetOtp(ForgotPasswordDto dto, String clientIp) {
        if (!props.passwordReset().enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Password reset is not enabled.");
        }

        if (clientIp != null && !clientIp.isBlank()) {
            int attempts = ipOtpLimiter.asMap().compute(clientIp, (k, v) -> v == null ? 1 : v + 1);
            if (attempts > 5) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many password reset requests from this IP. Please try again later.");
            }
        }

        String email = AuthService.normalizeEmail(dto.email());
        Optional<Restaurant> restaurant = restaurantRepository.findByEmail(email);

        // Enumeration-safe: always return the generic message even if email is not found or deleted
        if (restaurant.isEmpty() || restaurant.get().isDeleted()) {
            return new MessageResponseDto(GENERIC_OTP_SENT_MESSAGE);
        }

        // 60-second resend cooldown
        Optional<PasswordResetOtp> activeOtp = otpRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, Instant.now());
        if (activeOtp.isPresent() && Duration.between(activeOtp.get().getCreatedAt(), Instant.now()).getSeconds() < 60) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait at least 60 seconds before requesting another verification code.");
        }

        otpRepository.invalidateExistingOtps(email);
        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES);

        PasswordResetOtp otp = PasswordResetOtp.builder()
                .email(email)
                .otpCode(passwordEncoder.encode(code))
                .attempts(0)
                .expiresAt(expiresAt)
                .used(false)
                .build();
        otpRepository.save(otp);

        emailSender.sendPasswordResetOtp(email, code);

        return new MessageResponseDto(GENERIC_OTP_SENT_MESSAGE);
    }

    @Transactional
    public MessageResponseDto resetPasswordWithOtp(ResetPasswordOtpDto dto) {
        if (!props.passwordReset().enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Password reset is not enabled.");
        }

        String email = AuthService.normalizeEmail(dto.email());
        String code = dto.otp() != null ? dto.otp().strip() : "";

        PasswordResetOtp otp = otpRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, Instant.now())
                .orElseThrow(() -> new InvalidFieldException("otp", "Invalid or expired verification code. Please request a new one."));

        if (otp.getAttempts() >= 5) {
            otp.setUsed(true);
            otpRepository.save(otp);
            throw new InvalidFieldException("otp", "Too many failed attempts. This verification code has been invalidated.");
        }

        if (!passwordEncoder.matches(code, otp.getOtpCode())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otp.setLastAttemptAt(Instant.now());
            if (otp.getAttempts() >= 5) {
                otp.setUsed(true);
            }
            otpRepository.save(otp);
            int remaining = Math.max(0, 5 - otp.getAttempts());
            if (remaining == 0) {
                throw new InvalidFieldException("otp", "Too many failed attempts. This verification code has been invalidated.");
            }
            throw new InvalidFieldException("otp", "Incorrect verification code. " + remaining + " attempt(s) remaining.");
        }

        Restaurant restaurant = restaurantRepository.findByEmail(email)
                .orElseThrow(() -> new ConflictException("email", "Restaurant account not found."));

        otp.setUsed(true);
        otpRepository.save(otp);

        restaurant.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        restaurant.setMustChangePassword(false);
        restaurant.setPasswordChangedAt(Instant.now());
        restaurant.incrementTokenVersion();
        restaurantRepository.save(restaurant);

        log.info("Password successfully reset via OTP for restaurant {} ({})", restaurant.getSlug(), email);
        return new MessageResponseDto("Your password has been successfully reset. You can now sign in.");
    }
}
