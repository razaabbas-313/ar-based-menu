package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.ForgotPasswordDto;
import com.atelier.media.dto.MessageResponseDto;
import com.atelier.media.dto.ResetPasswordOtpDto;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.model.PasswordResetOtp;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.repository.PasswordResetOtpRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OtpServiceTest {

    private PasswordResetOtpRepository otpRepository;
    private RestaurantRepository restaurantRepository;
    private PasswordEncoder passwordEncoder;
    private EmailSender emailSender;
    private AtelierProperties propsEnabled;
    private AtelierProperties propsDisabled;

    private OtpService enabledService;
    private OtpService disabledService;

    @BeforeEach
    void setUp() {
        otpRepository = mock(PasswordResetOtpRepository.class);
        restaurantRepository = mock(RestaurantRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        emailSender = mock(EmailSender.class);

        propsEnabled = new AtelierProperties(
                "restaurant-atelier",
                "restaurant-atelier-private",
                java.time.Duration.ofMinutes(5),
                java.time.Duration.ofMinutes(15),
                8L * 1024 * 1024,
                50L * 1024 * 1024,
                25L * 1024 * 1024,
                4L * 1024 * 1024,
                500,
                200,
                new AtelierProperties.Jwt("test-secret-01234567890123456789012345678901", "atelier", java.time.Duration.ofHours(1), java.time.Duration.ofHours(1)),
                new AtelierProperties.Cors(java.util.List.of("*")),
                new AtelierProperties.Login(5, java.time.Duration.ofMinutes(15)),
                new AtelierProperties.SuperAdmin("admin@atelier.local", null, false),
                new AtelierProperties.PasswordReset(true),
                java.util.List.of()
        );

        propsDisabled = new AtelierProperties(
                "restaurant-atelier",
                "restaurant-atelier-private",
                java.time.Duration.ofMinutes(5),
                java.time.Duration.ofMinutes(15),
                8L * 1024 * 1024,
                50L * 1024 * 1024,
                25L * 1024 * 1024,
                4L * 1024 * 1024,
                500,
                200,
                new AtelierProperties.Jwt("test-secret-01234567890123456789012345678901", "atelier", java.time.Duration.ofHours(1), java.time.Duration.ofHours(1)),
                new AtelierProperties.Cors(java.util.List.of("*")),
                new AtelierProperties.Login(5, java.time.Duration.ofMinutes(15)),
                new AtelierProperties.SuperAdmin("admin@atelier.local", null, false),
                new AtelierProperties.PasswordReset(false),
                java.util.List.of()
        );

        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        enabledService = new OtpService(otpRepository, restaurantRepository, passwordEncoder, emailSender, propsEnabled, transactions);
        disabledService = new OtpService(otpRepository, restaurantRepository, passwordEncoder, emailSender, propsDisabled, transactions);
    }

    @Test
    void disabledFlagReturns404() {
        assertThatThrownBy(() -> disabledService.sendPasswordResetOtp(new ForgotPasswordDto("a@x.com"), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        assertThatThrownBy(() -> disabledService.resetPasswordWithOtp(new ResetPasswordOtpDto("a@x.com", "123456", "newPass123!")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void enumerationSafetyReturnsSamePayloadForExistingAndNonExistingEmails() {
        Restaurant existing = TestFixtures.restaurant(1L, "bistro-a");
        existing.setEmail("chef@bistro.com");
        when(restaurantRepository.findByEmailForUpdate("chef@bistro.com")).thenReturn(Optional.of(existing));
        when(restaurantRepository.findByEmailForUpdate("nonexistent@nowhere.com")).thenReturn(Optional.empty());

        MessageResponseDto existingResp = enabledService.sendPasswordResetOtp(new ForgotPasswordDto("chef@bistro.com"), "127.0.0.1");
        MessageResponseDto nonExistingResp = enabledService.sendPasswordResetOtp(new ForgotPasswordDto("nonexistent@nowhere.com"), "127.0.0.1");

        assertThat(existingResp.message()).isEqualTo(OtpService.GENERIC_OTP_SENT_MESSAGE);
        assertThat(nonExistingResp.message()).isEqualTo(OtpService.GENERIC_OTP_SENT_MESSAGE);

        // Verify only the existing email triggered OTP persistence and email sending
        verify(emailSender, times(1)).sendPasswordResetOtp(eq("chef@bistro.com"), anyString());
        verify(emailSender, never()).sendPasswordResetOtp(eq("nonexistent@nowhere.com"), anyString());
    }

    @Test
    void rateLimitedResendWithin60Seconds() {
        Restaurant existing = TestFixtures.restaurant(1L, "bistro-a");
        existing.setEmail("chef@bistro.com");
        when(restaurantRepository.findByEmailForUpdate("chef@bistro.com")).thenReturn(Optional.of(existing));

        PasswordResetOtp recentOtp = PasswordResetOtp.builder()
                .id(1L)
                .email("chef@bistro.com")
                .otpCode("hash")
                .createdAt(Instant.now().minusSeconds(30)) // 30 seconds ago (< 60s)
                .expiresAt(Instant.now().plusSeconds(500))
                .used(false)
                .build();
        when(otpRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq("chef@bistro.com"), any(Instant.class)))
                .thenReturn(Optional.of(recentOtp));

        assertThatThrownBy(() -> enabledService.sendPasswordResetOtp(new ForgotPasswordDto("chef@bistro.com"), "127.0.0.1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");
    }

    @Test
    void wrongOtpIncrementsAttemptsAndLocksOutAfter5() {
        when(restaurantRepository.findByEmailForUpdate("chef@bistro.com"))
                .thenReturn(Optional.of(TestFixtures.restaurant(1L, "bistro-a")));
        String realCode = "654321";
        String hashedCode = passwordEncoder.encode(realCode);

        PasswordResetOtp otp = PasswordResetOtp.builder()
                .id(1L)
                .email("chef@bistro.com")
                .otpCode(hashedCode)
                .attempts(4) // 4 prior wrong attempts
                .createdAt(Instant.now().minusSeconds(100))
                .expiresAt(Instant.now().plusSeconds(500))
                .used(false)
                .build();

        when(otpRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq("chef@bistro.com"), any(Instant.class)))
                .thenReturn(Optional.of(otp));

        // 5th wrong attempt should invalidate the code and throw error
        assertThatThrownBy(() -> enabledService.resetPasswordWithOtp(new ResetPasswordOtpDto("chef@bistro.com", "000000", "newPassword123!")))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("Too many failed attempts");

        assertThat(otp.getAttempts()).isEqualTo(5);
        assertThat(otp.getUsed()).isTrue();
        verify(otpRepository).save(otp);
    }

    @ParameterizedTest
    @EnumSource(value = RestaurantStatus.class, names = "SUSPENDED", mode = EnumSource.Mode.EXCLUDE)
    void successfulResetInvalidatesOtpAndUpdatesPassword(RestaurantStatus status) {
        String realCode = "789123";
        String hashedCode = passwordEncoder.encode(realCode);

        PasswordResetOtp otp = PasswordResetOtp.builder()
                .id(1L)
                .email("chef@bistro.com")
                .otpCode(hashedCode)
                .attempts(1)
                .createdAt(Instant.now().minusSeconds(100))
                .expiresAt(Instant.now().plusSeconds(500))
                .used(false)
                .build();

        Restaurant restaurant = TestFixtures.restaurant(1L, "bistro-a");
        restaurant.setEmail("chef@bistro.com");
        restaurant.setStatus(status);

        when(otpRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(eq("chef@bistro.com"), any(Instant.class)))
                .thenReturn(Optional.of(otp));
        when(restaurantRepository.findByEmailForUpdate("chef@bistro.com")).thenReturn(Optional.of(restaurant));

        MessageResponseDto resp = enabledService.resetPasswordWithOtp(new ResetPasswordOtpDto("chef@bistro.com", "789123", "NewSecretPass@123"));

        assertThat(resp.message()).contains("successfully reset");
        assertThat(otp.getUsed()).isTrue();
        verify(otpRepository).save(otp);
        verify(restaurantRepository).save(restaurant);
        assertThat(passwordEncoder.matches("NewSecretPass@123", restaurant.getPasswordHash())).isTrue();
        assertThat(restaurant.getStatus()).isEqualTo(status);
    }

    @Test
    void resetRequestsAreBoundedByNormalizedEmailEvenWithoutAnActiveCode() {
        ResetPasswordOtpDto request = new ResetPasswordOtpDto(" CHEF@BISTRO.COM ", "000000", "newPassword123!");
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> enabledService.resetPasswordWithOtp(request))
                    .isInstanceOf(InvalidFieldException.class);
        }
        assertThatThrownBy(() -> enabledService.resetPasswordWithOtp(
                new ResetPasswordOtpDto("chef@bistro.com", "000000", "newPassword123!")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("429");
        verify(restaurantRepository, times(10)).findByEmailForUpdate("chef@bistro.com");
    }
}
