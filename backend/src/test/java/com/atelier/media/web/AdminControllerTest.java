package com.atelier.media.web;

import com.atelier.media.dto.AuthResponseDto;
import com.atelier.media.dto.RestaurantDto;
import com.atelier.media.dto.admin.AdminAuthResponseDto;
import com.atelier.media.dto.admin.AdminProfileDto;
import com.atelier.media.dto.admin.AdminRestaurantCreatedDto;
import com.atelier.media.dto.admin.TemporaryPasswordDto;
import com.atelier.media.dto.SlugAvailabilityDto;
import com.atelier.media.dto.admin.AdminRestaurantDto;
import com.atelier.media.dto.admin.PageDto;
import com.atelier.media.dto.admin.PlatformAnalyticsDto;
import com.atelier.media.exception.InvalidCredentialsException;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.security.SecurityConfig;
import com.atelier.media.security.TokenService;
import com.atelier.media.service.AdminAuthService;
import com.atelier.media.service.AdminService;
import com.atelier.media.service.ModelRequestService;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminController.class, AdminAuthController.class})
@Import({SecurityConfig.class, TokenService.class})
@ActiveProfiles("test")
class AdminControllerTest {

    private static final AdminPrincipal ADMIN = new AdminPrincipal(1L, "owner@platform.test");
    private static final AdminRestaurantDto ROW = new AdminRestaurantDto(7L, "Bistro A", "bistro-a", "O", "a@x.com",
            RestaurantStatus.ACTIVE, 3, 1, 1_500_000L, "Starter", null, Instant.now(), null, null, true, null);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private AdminService adminService;

    @MockBean
    private AdminAuthService adminAuthService;

    @MockBean
    private com.atelier.media.service.PublicMenuService publicMenuService;

    @MockBean
    private com.atelier.media.security.ClientIpResolver clientIpResolver;

    @MockBean
    private ModelRequestService modelRequestService;



    @Test
    void adminRestaurantMenuPreviewReturnsStaffPreview() throws Exception {
        when(publicMenuService.getStaffPreview(7L)).thenReturn(RestaurantControllerTest.menuA());
        mvc.perform(get("/api/v1/admin/restaurants/7/menu-preview").with(TestFixtures.superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurant.name").value("Bistro A"));
        verify(publicMenuService).getStaffPreview(7L);
    }

    @Test
    void adminRoutesRejectAnonymousAndRestaurantSessions() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/analytics").with(TestFixtures.staff(7L, "bistro-a"))).andExpect(status().isForbidden());

        String realRestaurantToken = tokenService.issue(TestFixtures.restaurant(7L, "bistro-a")).value();
        mvc.perform(get("/api/v1/admin/restaurants").header("Authorization", "Bearer " + realRestaurantToken))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminService);
    }

    @Test
    void analyticsForTheSuperAdmin() throws Exception {
        when(adminService.analytics()).thenReturn(new PlatformAnalyticsDto(4, 2, 1, 0, 1, 40, 12,
                3L * 1024 * 1024 * 1024, List.of(ROW), List.of(ROW), Instant.now()));

        mvc.perform(get("/api/v1/admin/analytics").with(TestFixtures.superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRestaurants").value(4))
                .andExpect(jsonPath("$.totalStorageBytes").value(3221225472L))
                .andExpect(jsonPath("$.topByStorage[0].slug").value("bistro-a"));
    }

    @Test
    void restaurantListIsPaginatedAndFiltered() throws Exception {
        when(adminService.list(eq("bistro"), eq(RestaurantStatus.ACTIVE), anyBoolean(), eq(0), eq(20), eq("storage"), eq(false)))
                .thenReturn(new PageDto<>(List.of(ROW), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/admin/restaurants").with(TestFixtures.superAdmin())
                        .param("q", "bistro").param("status", "ACTIVE").param("sort", "storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].bandwidthTier").value("Starter"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createRestaurantPassesTheAdminPrincipal() throws Exception {
        when(adminService.create(any(), eq(ADMIN))).thenReturn(new AdminRestaurantCreatedDto(ROW, "Temp-Pass-123"));

        mvc.perform(post("/api/v1/admin/restaurants").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bistro A\",\"ownerName\":\"O\",\"email\":\"a@x.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temporaryPassword").value("Temp-Pass-123"));
    }

    @Test
    void createRestaurantRejectsABadPhoneAndNeverTakesAPasswordFromTheAdmin() throws Exception {
        mvc.perform(post("/api/v1/admin/restaurants").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bistro A\",\"ownerName\":\"O\",\"email\":\"a@x.com\",\"phoneNumber\":\"call me\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.phoneNumber").exists());

        when(adminService.create(any(), eq(ADMIN))).thenReturn(new AdminRestaurantCreatedDto(ROW, "Temp-abcdEFGH23"));
        mvc.perform(post("/api/v1/admin/restaurants").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bistro A\",\"slug\":\"bistro-a\",\"ownerName\":\"O\",\"email\":\"a@x.com\",\"phoneNumber\":\"+92 300 1234567\",\"password\":\"ignored-by-server\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurant.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").value("Temp-abcdEFGH23"));
    }

    @Test
    void provisioningEndpointsAreSuperAdminOnly() throws Exception {
        mvc.perform(post("/api/v1/admin/restaurants").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bistro A\",\"ownerName\":\"O\",\"email\":\"a@x.com\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/restaurants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bistro A\",\"ownerName\":\"O\",\"email\":\"a@x.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/restaurants/7/reset-password").with(TestFixtures.staff(7L, "bistro-a")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/slug-available").param("slug", "bistro-a"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPasswordReturnsANewTemporaryPasswordOnce() throws Exception {
        when(adminService.resetOwnerPassword(7L, ADMIN)).thenReturn(new TemporaryPasswordDto(ROW, "Temp-NewPass234"));

        mvc.perform(post("/api/v1/admin/restaurants/7/reset-password").with(TestFixtures.superAdmin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.temporaryPassword").value("Temp-NewPass234"));
    }

    @Test
    void slugAvailabilityForTheProvisioningForm() throws Exception {
        when(adminService.slugAvailability("Saffron House", null)).thenReturn(new SlugAvailabilityDto("saffron-house", true, null));

        mvc.perform(get("/api/v1/admin/slug-available").with(TestFixtures.superAdmin()).param("slug", "Saffron House"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("saffron-house"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void statusUpdateRequiresAKnownStatus() throws Exception {
        mvc.perform(put("/api/v1/admin/restaurants/7/status").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BANNED\"}"))
                .andExpect(status().isBadRequest());

        when(adminService.updateStatus(eq(7L), any(), eq(ADMIN))).thenReturn(ROW);
        mvc.perform(put("/api/v1/admin/restaurants/7/status").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"reason\":\"unpaid\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteIsSoftByDefaultAndHardOnRequest() throws Exception {
        when(adminService.softDelete(7L, ADMIN)).thenReturn(ROW);
        when(adminService.hardDelete(7L, ADMIN)).thenReturn(new AdminService.HardDeleteResult(7L, "bistro-a", 12));

        mvc.perform(delete("/api/v1/admin/restaurants/7").with(TestFixtures.superAdmin())).andExpect(status().isOk());
        verify(adminService).softDelete(7L, ADMIN);

        mvc.perform(delete("/api/v1/admin/restaurants/7").param("hard", "true").with(TestFixtures.superAdmin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.objectsScheduled").value(12));
    }

    @Test
    void impersonationReturnsARestaurantSession() throws Exception {
        when(adminService.impersonate(7L, ADMIN)).thenReturn(new AuthResponseDto("tok", "Bearer", Instant.now(), 3600,
                new RestaurantDto(7L, "Bistro A", "bistro-a", "O", "a@x.com", RestaurantStatus.ACTIVE, Instant.now()),
                "owner@platform.test"));

        mvc.perform(post("/api/v1/admin/restaurants/7/impersonate").with(TestFixtures.superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impersonatedBy").value("owner@platform.test"))
                .andExpect(jsonPath("$.restaurant.slug").value("bistro-a"));
    }

    @Test
    void adminLoginIsPublicAndWrongPasswordIs401() throws Exception {
        when(adminAuthService.login(any(), any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@platform.test\",\"password\":\"nope-nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLoginSucceeds() throws Exception {
        when(adminAuthService.login(any(), any())).thenReturn(new AdminAuthResponseDto("admintok", "Bearer", Instant.now(), 43200,
                new AdminProfileDto(1L, "owner@platform.test", "Owner", true, null)));

        mvc.perform(post("/api/v1/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@platform.test\",\"password\":\"right-one-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin.mustChangePassword").value(true));
    }

    @Test
    void changePasswordValidatesLength() throws Exception {
        mvc.perform(put("/api/v1/admin/me/password").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.newPassword").exists());
    }

    @Test
    void get3dRequestsHistoryRequiresSuperAdminAndReturnsList() throws Exception {
        mvc.perform(get("/api/v1/admin/3d-requests/history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/3d-requests/history").with(TestFixtures.staff(7L, "bistro-a"))).andExpect(status().isForbidden());

        when(modelRequestService.getAllHistory(any(), any(), anyInt(), anyInt())).thenReturn(
                new com.atelier.media.dto.admin.PageDto<>(
                        List.of(
                                new com.atelier.media.dto.ModelRequestHistoryDto(
                                        10L, 7L, "Bistro A", "bistro-a", 101L, "DSH-0001", "Wagyu Steak", "https://img.jpg",
                                        "COMPLETED", "NEW", "[]", "Notes", null, "s3/model.glb", "https://s3/model.glb",
                                        Instant.now(), Instant.now()
                                )
                        ),
                        0, 50, 1L, 1
                )
        );

        mvc.perform(get("/api/v1/admin/3d-requests/history").with(TestFixtures.superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dishCode").value("DSH-0001"))
                .andExpect(jsonPath("$.items[0].dishTitle").value("Wagyu Steak"))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void update3dPermissionRequiresSuperAdminAndUpdatesSetting() throws Exception {
        mvc.perform(put("/api/v1/admin/restaurants/7/permission-3d").contentType(MediaType.APPLICATION_JSON)
                .content("{\"canUpload3d\":true}")).andExpect(status().isUnauthorized());

        mvc.perform(put("/api/v1/admin/restaurants/7/permission-3d").with(TestFixtures.staff(7L, "bistro-a"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"canUpload3d\":true}")).andExpect(status().isForbidden());

        when(adminService.update3dPermission(eq(7L), eq(true), any())).thenReturn(ROW);

        mvc.perform(put("/api/v1/admin/restaurants/7/permission-3d").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"canUpload3d\":true}"))
                .andExpect(status().isOk());

        verify(adminService).update3dPermission(eq(7L), eq(true), any());
    }
}
