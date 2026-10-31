package com.atelier.media.web;

import com.atelier.media.model.PlatformUser;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.repository.PlatformUserRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.SecurityConfig;
import com.atelier.media.security.TokenService;
import com.atelier.media.service.AdminAccess;
import com.atelier.media.service.EntitlementService;
import com.atelier.media.service.RestaurantAccess;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual security chain and current-account guards; business persistence is mocked in this slice. */
@WebMvcTest({PlanController.class, OwnerEntitlementController.class})
@Import({SecurityConfig.class, TokenService.class, AdminAccess.class, RestaurantAccess.class})
@ActiveProfiles("test")
class EntitlementControllerTest {
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @MockBean PlatformUserRepository users;
    @MockBean RestaurantRepository restaurants;
    @MockBean EntitlementService service;
    PlatformUser admin;
    Restaurant restaurant;

    @BeforeEach void accounts() {
        admin=PlatformUser.builder().id(1L).email("plan-operator@platform.test").displayName("Plan operator")
                .passwordHash("test-only-placeholder").mustChangePassword(false).build();
        restaurant=TestFixtures.restaurant(7L,"plan-bistro");
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant));
    }
    static Stream<Arguments> adminRoutes() {
        return Stream.of(
                Arguments.of("GET","/api/v1/admin/plans"),
                Arguments.of("POST","/api/v1/admin/plans"),
                Arguments.of("PUT","/api/v1/admin/plans/1"),
                Arguments.of("GET","/api/v1/admin/plans/1/revisions"),
                Arguments.of("GET","/api/v1/admin/features"),
                Arguments.of("GET","/api/v1/admin/restaurants/7/entitlements"),
                Arguments.of("POST","/api/v1/admin/restaurants/7/entitlements/assignment"),
                Arguments.of("PUT","/api/v1/admin/restaurants/7/entitlements/overrides/quota.dishes"),
                Arguments.of("DELETE","/api/v1/admin/restaurants/7/entitlements/overrides/quota.dishes"));
    }
    @ParameterizedTest @MethodSource("adminRoutes")
    void everyAdminEntitlementRouteRejectsAnonymousRequests(String method,String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method),path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @MethodSource("adminRoutes")
    void restaurantOwnerCannotReadOrMutatePlatformEntitlementRoutes(String method,String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method),path).with(TestFixtures.staff(7L,"plan-bistro"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @MethodSource("adminRoutes")
    void revokedAdminTokenIsRejectedOnEveryNewRoute(String method,String path) throws Exception {
        String issued=tokens.issueAdmin(admin).value();admin.incrementTokenVersion();
        mvc.perform(request(HttpMethod.valueOf(method),path).header("Authorization","Bearer "+issued)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @MethodSource("adminRoutes")
    void demotedAdminIsRejectedOnEveryNewRoute(String method,String path) throws Exception {
        String issued=tokens.issueAdmin(admin).value();admin.setRole("ROLE_RESTAURANT");
        mvc.perform(request(HttpMethod.valueOf(method),path).header("Authorization","Bearer "+issued)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void activeAdminCanReadPlansAndResponseIsNotCached() throws Exception {
        when(service.plans()).thenReturn(List.of(Map.of("id",1L,"name","Fixture plan","version",1)));
        mvc.perform(get("/api/v1/admin/plans").header("Authorization","Bearer "+tokens.issueAdmin(admin).value()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$[0].name").value("Fixture plan"));
    }
    @Test void ownerOverviewAlwaysUsesAuthenticatedTenantInsteadOfQueryParameter() throws Exception {
        when(service.effective(7L,false)).thenReturn(Map.of("planName","Assigned plan","values",Map.of("quota.dishes",10)));
        mvc.perform(get("/api/v1/studio/entitlements?restaurantId=99").with(TestFixtures.staff(7L,"plan-bistro")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.planName").value("Assigned plan"));
        verify(service).effective(7L,false);verify(service,never()).effective(eq(99L),anyBoolean());
    }
    @ParameterizedTest @ValueSource(strings={"revoked","suspended","deleted","missing","password-required"})
    void unavailableRestaurantCannotReadEntitlementOverview(String state) throws Exception {
        String token=tokens.issue(restaurant).value();int expected=401;
        switch(state) {
            case "revoked" -> restaurant.incrementTokenVersion();
            case "suspended" -> {restaurant.setStatus(RestaurantStatus.SUSPENDED);expected=403;}
            case "deleted" -> restaurant.setDeletedAt(Instant.now());
            case "missing" -> when(restaurants.findById(7L)).thenReturn(Optional.empty());
            case "password-required" -> {restaurant.setMustChangePassword(true);expected=403;}
        }
        mvc.perform(get("/api/v1/studio/entitlements").header("Authorization","Bearer "+token)).andExpect(status().is(expected));
        verifyNoInteractions(service);
    }
    @Test void ownerHasNoEntitlementMutationEndpoint() throws Exception {
        mvc.perform(put("/api/v1/studio/entitlements").with(TestFixtures.staff(7L,"plan-bistro"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quota.dishes\":999}"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }
    @Test void overrideRemovalKeepsPrivateReasonInJsonBodyAndUsesCurrentAdministrator() throws Exception {
        when(service.removeOverride(eq(7L),eq("quota.dishes"),eq(3L),eq("Private operator note"),any())).thenReturn(Map.of("configVersion",4));
        mvc.perform(delete("/api/v1/admin/restaurants/7/entitlements/overrides/quota.dishes")
                .with(TestFixtures.superAdmin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":3,\"reason\":\"Private operator note\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).removeOverride(eq(7L),eq("quota.dishes"),eq(3L),eq("Private operator note"),any());
    }
}
