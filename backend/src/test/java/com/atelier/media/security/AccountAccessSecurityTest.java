package com.atelier.media.security;

import com.atelier.media.model.PlatformUser;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.repository.PlatformUserRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.service.*;
import com.atelier.media.support.TestFixtures;
import com.atelier.media.web.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Signed bearer tokens through the real security chain and account gates; no live DB or AWS. */
@WebMvcTest({AdminController.class, AdminAuthController.class, StudioController.class, PublicMenuController.class})
@Import({SecurityConfig.class, TokenService.class, AdminAccess.class, AdminAuthService.class, RestaurantAccess.class})
@ActiveProfiles("test")
class AccountAccessSecurityTest {

    @Autowired private MockMvc mvc;
    @Autowired private TokenService tokens;
    @MockBean private PlatformUserRepository users;
    @MockBean private RestaurantRepository restaurants;
    @MockBean private LoginThrottle loginThrottle;
    @MockBean private ClientIpResolver clientIpResolver;
    @MockBean private AdminService adminService;
    @MockBean private DishService dishService;
    @MockBean private BrandingService brandingService;
    @MockBean private CategoryService categoryService;
    @MockBean private ModelRequestService modelRequestService;
    @MockBean private AnalyticsService analyticsService;
    @MockBean private PublicMenuService publicMenuService;
    @MockBean private MenuCache menuCache;

    private PlatformUser admin;
    private Restaurant restaurant;

    @BeforeEach
    void accounts() {
        admin = PlatformUser.builder().id(1L).email("owner@platform.test")
                .displayName("Owner").passwordHash("test-hash").build();
        restaurant = TestFixtures.restaurant(7L, "bistro-a");
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant));
    }

    static Stream<Arguments> adminRoutes() {
        return Stream.concat(routes(AdminController.class, "/api/v1/admin"),
                routes(AdminAuthController.class, "").filter(r -> r.path().startsWith("/api/v1/admin/")))
                .map(r -> Arguments.of(r.method(), r.path()));
    }

    static Stream<Arguments> studioRoutesAndStates() {
        return routes(StudioController.class, "/api/v1/studio")
                .flatMap(r -> Stream.of("revoked", "suspended", "deleted", "missing", "password-required")
                        .map(state -> Arguments.of(r.method(), r.path(), state)));
    }

    // Discover every mapped operation so newly added handlers inherit the regression coverage.
    private static Stream<Route> routes(Class<?> controller, String prefix) {
        return Arrays.stream(controller.getDeclaredMethods()).flatMap(method -> {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) return Stream.empty();
            return Arrays.stream(mapping.method()).flatMap(verb -> Arrays.stream(mapping.path())
                    .map(path -> new Route(verb.name(), (prefix + path).replaceAll("\\{[^}]+}", "7"))));
        });
    }

    private record Route(String method, String path) { }

    @ParameterizedTest(name = "revoked admin: {0} {1}")
    @MethodSource("adminRoutes")
    void revokedAdminCannotPerformAnyAdminOperation(String method, String path) throws Exception {
        String token = tokens.issueAdmin(admin).value();
        admin.incrementTokenVersion();
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(adminBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Sign-in required"));
        verifyNoInteractions(adminService, modelRequestService, publicMenuService, analyticsService);
        verify(users, never()).save(any());
    }

    @ParameterizedTest(name = "missing admin: {0} {1}")
    @MethodSource("adminRoutes")
    void deletedAdminCannotPerformAnyAdminOperation(String method, String path) throws Exception {
        String token = tokens.issueAdmin(admin).value();
        when(users.findById(1L)).thenReturn(Optional.empty());
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(adminBody()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminService, modelRequestService, publicMenuService, analyticsService);
    }

    @ParameterizedTest(name = "studio {2}: {0} {1}")
    @MethodSource("studioRoutesAndStates")
    void unavailableRestaurantCannotPerformAnyStudioOperation(String method, String path, String state) throws Exception {
        String token = tokens.issue(restaurant).value();
        int expectedStatus = 401;
        switch (state) {
            case "revoked" -> restaurant.incrementTokenVersion();
            case "suspended" -> { restaurant.setStatus(RestaurantStatus.SUSPENDED); expectedStatus = 403; }
            case "deleted" -> restaurant.setDeletedAt(java.time.Instant.now());
            case "missing" -> when(restaurants.findById(7L)).thenReturn(Optional.empty());
            case "password-required" -> { restaurant.setMustChangePassword(true); expectedStatus = 403; }
            default -> throw new IllegalArgumentException(state);
        }
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(expectedStatus));
        verifyNoInteractions(dishService, categoryService, brandingService, modelRequestService,
                analyticsService, publicMenuService, menuCache);
        verify(restaurants, never()).save(any());
    }

    @Test
    void adminRevocationIsObservedAfterPreviouslySuccessfulRequestAndNewTokenWorks() throws Exception {
        String oldToken = tokens.issueAdmin(admin).value();
        mvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isOk());
        admin.incrementTokenVersion();
        mvc.perform(get("/api/v1/admin/restaurants").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/restaurants").header("Authorization", "Bearer " + tokens.issueAdmin(admin).value()))
                .andExpect(status().isOk());
        verify(adminService, times(1)).list(any(), any(), anyBoolean(), anyInt(), anyInt(), any(), anyBoolean());
    }

    @Test
    void restaurantRevocationNeedsNoCacheInvalidationAndNewTokenWorks() throws Exception {
        String oldToken = tokens.issue(restaurant).value();
        mvc.perform(get("/api/v1/studio/categories").header("Authorization", "Bearer " + oldToken)).andExpect(status().isOk());
        restaurant.incrementTokenVersion();
        mvc.perform(get("/api/v1/studio/categories").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/studio/categories").header("Authorization", "Bearer " + tokens.issue(restaurant).value()))
                .andExpect(status().isOk());
        verify(categoryService, times(2)).getCategories(7L);
    }

    @Test
    void setupTokensAndCrossAudienceTokensRemainRestricted() throws Exception {
        restaurant.setMustChangePassword(true);
        mvc.perform(get("/api/v1/studio/menu-preview").header("Authorization", "Bearer " + tokens.issue(restaurant).value()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("Password change required"));
        mvc.perform(get("/api/v1/studio/categories").header("Authorization", "Bearer " + tokens.issueAdmin(admin).value()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/restaurants").header("Authorization", "Bearer " + tokens.issue(restaurant).value()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminService, categoryService, publicMenuService);
    }

    @Test
    void supportSessionPreservesPasswordSetupExceptionButNotSuspensionBypass() throws Exception {
        restaurant.setMustChangePassword(true);
        String supportToken = tokens.issueImpersonation(restaurant, admin.getEmail()).value();
        mvc.perform(get("/api/v1/studio/categories").header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk());
        restaurant.setStatus(RestaurantStatus.SUSPENDED);
        mvc.perform(get("/api/v1/studio/categories").header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isForbidden());
        verify(categoryService, times(1)).getCategories(7L);
    }

    @Test
    void publicMenuAndAnonymousAccessRulesAreUnchanged() throws Exception {
        mvc.perform(get("/api/v1/public/menu/bistro-a")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/restaurants")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/studio/categories")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users, restaurants, categoryService, adminService);
        verify(publicMenuService).getMenu("bistro-a");
    }

    private static String adminBody() {
        return "{\"currentPassword\":\"OldPassword123!\",\"newPassword\":\"NewPassword123!\"}";
    }
}
