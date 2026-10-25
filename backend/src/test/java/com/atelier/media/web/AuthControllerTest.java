package com.atelier.media.web;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.AuthResponseDto;
import com.atelier.media.dto.RestaurantDto;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.InvalidCredentialsException;
import com.atelier.media.exception.AccountSuspendedException;
import com.atelier.media.exception.TooManyAttemptsException;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.security.SecurityConfig;
import com.atelier.media.security.Tenant;
import com.atelier.media.security.TokenService;
import com.atelier.media.service.AuthService;
import com.atelier.media.service.OtpService;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, TokenService.class})
@ActiveProfiles("test")
class AuthControllerTest {

    private static final RestaurantDto PROFILE = new RestaurantDto(3L, "Bistro Lumière", "bistro-lumiere",
            "Camille", "owner@bistro.com", RestaurantStatus.TRIAL, Instant.parse("2026-01-01T00:00:00Z"));
    private static final RestaurantDto SETUP_PROFILE = new RestaurantDto(3L, "Bistro Lumière", "bistro-lumiere",
            "Camille", "owner@bistro.com", RestaurantStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"), true);

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private com.atelier.media.security.ClientIpResolver clientIpResolver;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private AtelierProperties props;

    @Test
    void authConfigReturnsPasswordResetFlag() throws Exception {
        mvc.perform(get("/api/v1/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordResetEnabled").value(false));
    }

    @Test
    void forgotPasswordReturns404WhenDisabled() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@bistro.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void forgotPasswordWorksWhenEnabled() throws Exception {
        org.mockito.Mockito.doReturn(new AtelierProperties.PasswordReset(true)).when(props).passwordReset();
        when(otpService.sendPasswordResetOtp(any(), any()))
                .thenReturn(new com.atelier.media.dto.MessageResponseDto("If an account exists for this email, a verification code has been sent."));
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@bistro.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account exists for this email, a verification code has been sent."));
    }

    @Test
    void publicSelfRegistrationIsClosed() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"Bistro Lumière","ownerName":"Camille","email":"owner@bistro.com","password":"saffron-2026"}
                        """))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/slug-available").param("slug", "bistro"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void loginReportsAForcedPasswordChange() throws Exception {
        when(authService.login(any(), any())).thenReturn(new AuthResponseDto("setup-token", "Bearer", Instant.now().plusSeconds(3600),
                3600, SETUP_PROFILE, null, true));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@bistro.com\",\"password\":\"Temp-abcDEF2345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.restaurant.mustChangePassword").value(true));
    }

    @Test
    void changePasswordWorksWithTheSetupTokenAndReturnsAFullToken() throws Exception {
        when(authService.changePassword(eq(new Tenant(3L, "bistro-lumiere")), any())).thenReturn(
                new AuthResponseDto("full-token", "Bearer", Instant.now().plusSeconds(3600), 3600, PROFILE, null, false));

        mvc.perform(post("/api/v1/auth/change-password").with(TestFixtures.setupStaff(3L, "bistro-lumiere"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Temp-abcDEF2345\",\"newPassword\":\"my-own-secret-1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").value("full-token"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void changePasswordRequiresATokenAndAtLeastEightCharacters() throws Exception {
        mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Temp-abcDEF2345\",\"newPassword\":\"my-own-secret-1\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/change-password").with(TestFixtures.setupStaff(3L, "bistro-lumiere"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Temp-abcDEF2345\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").value("must be 8-72 characters"));

        verifyNoInteractions(authService);
    }

    @Test
    void wrongCurrentPasswordIsA400OnThatField() throws Exception {
        when(authService.changePassword(any(), any()))
                .thenThrow(new InvalidFieldException("currentPassword", "The current password is incorrect."));

        mvc.perform(post("/api/v1/auth/change-password").with(TestFixtures.setupStaff(3L, "bistro-lumiere"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"nope-nope-1\",\"newPassword\":\"my-own-secret-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.currentPassword").value("The current password is incorrect."));
    }

    @Test
    void setupTokenCannotOpenTheStudioUntilThePasswordIsChanged() throws Exception {
        mvc.perform(get("/api/v1/studio/dishes").with(TestFixtures.setupStaff(3L, "bistro-lumiere")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value(SecurityConfig.PASSWORD_CHANGE_REQUIRED));
        mvc.perform(post("/api/v1/media/presign").with(TestFixtures.setupStaff(3L, "bistro-lumiere"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        when(authService.me(new Tenant(3L, "bistro-lumiere"))).thenReturn(SETUP_PROFILE);
        mvc.perform(get("/api/v1/auth/me").with(TestFixtures.setupStaff(3L, "bistro-lumiere")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        when(authService.login(any(), any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@bistro.com\",\"password\":\"wrong-one\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void lockedOutLoginIs429WithRetryAfter() throws Exception {
        when(authService.login(any(), any())).thenThrow(new TooManyAttemptsException(Duration.ofMinutes(15)));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@bistro.com\",\"password\":\"wrong-one\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
    }

    @Test
    void suspendedAccountIs403() throws Exception {
        when(authService.login(any(), any())).thenThrow(new AccountSuspendedException());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@bistro.com\",\"password\":\"right-one\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account suspended"));
    }

    @Test
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void meReturnsTheProfileOfTheTokenRestaurant() throws Exception {
        when(authService.me(new Tenant(3L, "bistro-lumiere"))).thenReturn(PROFILE);

        mvc.perform(get("/api/v1/auth/me").with(TestFixtures.staff(3L, "bistro-lumiere")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@bistro.com"));
    }
}
