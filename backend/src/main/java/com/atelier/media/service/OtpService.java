package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.ForgotPasswordDto;
import com.atelier.media.dto.MessageResponseDto;
import com.atelier.media.dto.ResetPasswordOtpDto;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    private final Cache<String, Integer> ipOtpLimiter = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    // Bound guesses across replacement codes as well as the durable five-attempt per-code cap.
    private final Cache<String, Integer> resetLimiter = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    public OtpService(PasswordResetOtpRepository otpRepository,
                      RestaurantRepository restaurantRepository,
                      PasswordEncoder passwordEncoder,
                      EmailSender emailSender,
                      AtelierProperties props,
                      PlatformTransactionManager transactionManager) {
        this.otpRepository = otpRepository;
        this.restaurantRepository = restaurantRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.props = props;
        this.tx = new TransactionTemplate(transactionManager);
        // Validation failures must stay committed even if a future caller rolls back its own work.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

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
        String code = tx.execute(status -> {
            Optional<Restaurant> restaurant = restaurantRepository.findByEmailForUpdate(email);
            // Preserve generic responses without granting recovery to unavailable accounts.
            if (restaurant.isEmpty() || !canReset(restaurant.get())) return null;

            Instant now = Instant.now();
            Optional<PasswordResetOtp> activeOtp = otpRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, now);
            if (activeOtp.isPresent() && Duration.between(activeOtp.get().getCreatedAt(), now).getSeconds() < 60) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait at least 60 seconds before requesting another verification code.");
            }

            otpRepository.invalidateExistingOtps(email);
            String generated = String.format("%06d", random.nextInt(1_000_000));
            otpRepository.save(PasswordResetOtp.builder()
                    .email(email).otpCode(passwordEncoder.encode(generated)).attempts(0)
                    .expiresAt(now.plus(10, ChronoUnit.MINUTES)).used(false).build());
            return generated;
        });

        // Delivery is outside the committed OTP transaction and its account lock.
        if (code != null) emailSender.sendPasswordResetOtp(email, code);

        return new MessageResponseDto(GENERIC_OTP_SENT_MESSAGE);
    }

    public MessageResponseDto resetPasswordWithOtp(ResetPasswordOtpDto dto) {
        if (!props.passwordReset().enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Password reset is not enabled.");
        }

        String email = AuthService.normalizeEmail(dto.email());
        String code = dto.otp() != null ? dto.otp().strip() : "";
        if (resetLimiter.asMap().compute(email, (k, v) -> v == null ? 1 : v + 1) > 10) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many password reset attempts. Please try again later.");
        }

        String failure = tx.execute(status -> {
            // The same account lock is acquired before any OTP read/write on both entry paths.
            Optional<Restaurant> found = restaurantRepository.findByEmailForUpdate(email);
            if (found.isEmpty() || !canReset(found.get())) {
                return "Invalid or expired verification code. Please request a new one.";
            }
            Restaurant restaurant = found.get();
            PasswordResetOtp otp = otpRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, Instant.now()).orElse(null);
            if (otp == null) return "Invalid or expired verification code. Please request a new one.";
            if (otp.getAttempts() >= 5) {
                otp.setUsed(true);
                otpRepository.save(otp);
                return "Too many failed attempts. This verification code has been invalidated.";
            }

            if (!passwordEncoder.matches(code, otp.getOtpCode())) {
                otp.setAttempts(otp.getAttempts() + 1);
                otp.setLastAttemptAt(Instant.now());
                if (otp.getAttempts() >= 5) otp.setUsed(true);
                otpRepository.save(otp);
                int remaining = Math.max(0, 5 - otp.getAttempts());
                return remaining == 0
                        ? "Too many failed attempts. This verification code has been invalidated."
                        : "Incorrect verification code. " + remaining + " attempt(s) remaining.";
            }

            otp.setUsed(true);
            otpRepository.save(otp);
            otpRepository.invalidateExistingOtps(email);
            restaurant.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
            restaurant.setMustChangePassword(false);
            restaurant.setPasswordChangedAt(Instant.now());
            restaurant.incrementTokenVersion();
            restaurantRepository.save(restaurant);
            return null;
        });
        // A validation exception inside tx would roll back the failure counter and lockout.
        if (failure != null) throw new InvalidFieldException("otp", failure);

        log.info("Password successfully reset via OTP");
        return new MessageResponseDto("Your password has been successfully reset. You can now sign in.");
    }

    private static boolean canReset(Restaurant restaurant) {
        return !restaurant.isDeleted() && restaurant.getStatus().canUseStudio();
    }
}
