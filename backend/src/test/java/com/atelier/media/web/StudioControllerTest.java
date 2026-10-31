package com.atelier.media.web;

import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.RestaurantBrandingDto;
import com.atelier.media.dto.StudioSettingsDto;
import com.atelier.media.exception.AccountSuspendedException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.model.DishCategory;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.security.SecurityConfig;
import com.atelier.media.security.Tenant;
import com.atelier.media.security.TokenService;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.service.AnalyticsService;
import com.atelier.media.service.BrandingService;
import com.atelier.media.service.CategoryService;
import com.atelier.media.service.DishService;
import com.atelier.media.service.MenuCache;
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
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudioController.class)
@Import({SecurityConfig.class, TokenService.class})
@ActiveProfiles("test")
class StudioControllerTest {

    private static final Tenant TENANT = new Tenant(7L, "bistro-a");
    private static final String DISH_BODY = """
            {"dishTitle":"Saffron Risotto","priceTag":24,"dishCategory":"MAINS","description":"Carnaroli",
             "dietaryTags":["vegetarian"],"available":false,"arPlacement":"table","dailySpecial":true}
            """;
    private static final String SETTINGS_BODY = """
            {"name":"Bistro A","tagline":"Seasonal","bio":"","openingHours":"Tue-Sun 18-23",
             "phoneNumber":"+33 1 23 45 67 89","whatsappNumber":"+33 6 12 34 56 78","instagramHandle":"@bistro",
             "facebookUrl":"","websiteUrl":"https://bistro.example","themeAccent":"gold","menuLayout":"compact",
             "currencyCode":"EUR","currencySymbol":"€"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DishService dishService;

    @MockBean
    private com.atelier.media.service.RestaurantAccess restaurantAccess;

    @MockBean
    private BrandingService brandingService;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private ModelRequestService modelRequestService;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private RestaurantRepository restaurantRepository;

    @MockBean
    private MenuCache menuCache;

    @MockBean
    private com.atelier.media.service.PublicMenuService publicMenuService;

    @Test
    void studioRequiresARestaurantSession() throws Exception {
        mvc.perform(get("/api/v1/studio/dishes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/studio/dishes").with(TestFixtures.superAdmin())).andExpect(status().isForbidden());
        verifyNoInteractions(dishService, brandingService);
    }

    @Test
    void menuPreviewRequiresRestaurantSessionAndReturnsTenantDishes() throws Exception {
        mvc.perform(get("/api/v1/studio/menu-preview")).andExpect(status().isUnauthorized());

        when(publicMenuService.getStaffPreview(7L)).thenReturn(RestaurantControllerTest.menuA());
        mvc.perform(get("/api/v1/studio/menu-preview").with(TestFixtures.staff(7L, "bistro-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurant.name").value("Bistro A"));
        verify(publicMenuService).getStaffPreview(7L);
    }

    @Test
    void listsOnlyTheTokenRestaurantsDishes() throws Exception {
        when(dishService.listDishes(TENANT)).thenReturn(List.of(TestFixtures.menuItem(1L, DishCategory.MAINS, true)));

        mvc.perform(get("/api/v1/studio/dishes").with(TestFixtures.staff(7L, "bistro-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        verify(dishService).listDishes(TENANT);
    }

    @Test
    void updateDishValidatesAndPassesTheTenant() throws Exception {
        MediaResponseDto updated = TestFixtures.menuItem(5L, DishCategory.MAINS, false);
        when(dishService.updateDish(eq(TENANT), eq(5L), any())).thenReturn(updated);

        mvc.perform(put("/api/v1/studio/dishes/5").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(DISH_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));

        mvc.perform(put("/api/v1/studio/dishes/5").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(DISH_BODY.replace("\"available\":false,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.available").exists());
    }

    @Test
    void availabilityToggle() throws Exception {
        when(dishService.setAvailability(TENANT, 5L, false)).thenReturn(TestFixtures.menuItem(5L, DishCategory.MAINS, false));

        mvc.perform(patch("/api/v1/studio/dishes/5/availability").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}"))
                .andExpect(status().isOk());
        verify(dishService).setAvailability(TENANT, 5L, false);
    }

    @Test
    void deletingAnotherRestaurantsDishIs404() throws Exception {
        doThrow(new MediaNotFoundException("Dish 99 not found")).when(dishService).deleteDish(TENANT, 99L);

        mvc.perform(delete("/api/v1/studio/dishes/99").with(TestFixtures.staff(7L, "bistro-a")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Dish 99 not found"));
    }

    @Test
    void ownerDeleteReturns204() throws Exception {
        mvc.perform(delete("/api/v1/studio/dishes/42").with(TestFixtures.staff(7L, "bistro-a")))
                .andExpect(status().isNoContent());
        verify(dishService).deleteDish(TENANT, 42L);
    }

    @Test
    void attach3dReturnsASignedUrl() throws Exception {
        when(dishService.requestModelUpload(eq(TENANT), eq(5L), any())).thenReturn(new PresignResponseDto(
                "https://s3/put", "restaurant-atelier/bistro-a/model-3d/u.glb", "https://cdn/u.glb", 5L,
                Instant.now(), 300L, Map.of("content-type", "model/gltf-binary")));

        mvc.perform(post("/api/v1/studio/dishes/5/attach-3d").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"dome.glb\",\"contentType\":\"model/gltf-binary\",\"uploadSizeBytes\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectKey").value("restaurant-atelier/bistro-a/model-3d/u.glb"));
    }

    @Test
    void suspendedRestaurantIs403() throws Exception {
        when(dishService.listDishes(TENANT)).thenThrow(new AccountSuspendedException());

        mvc.perform(get("/api/v1/studio/dishes").with(TestFixtures.staff(7L, "bistro-a")))
                .andExpect(status().isForbidden());
    }

    @Test
    void settingsRejectNonHttpLinks() throws Exception {
        mvc.perform(put("/api/v1/studio/settings").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SETTINGS_BODY.replace("https://bistro.example", "javascript:alert(1)")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.websiteUrl").exists());
        verifyNoInteractions(brandingService);
    }

    @Test
    void settingsUpdateWorks() throws Exception {
        RestaurantBrandingDto branding = new RestaurantBrandingDto(7L, "Bistro A", "bistro-a", "Seasonal", null, null, null,
                null, null, null, "bistro", null, null, "bistro.tok", null, "gold", "compact", "EUR", "€");
        when(brandingService.updateSettings(eq(TENANT), any()))
                .thenReturn(new StudioSettingsDto(branding, "O", "a@x.com", RestaurantStatus.ACTIVE, 0, 0, 0));

        mvc.perform(put("/api/v1/studio/settings").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(SETTINGS_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branding.themeAccent").value("gold"));
    }

    @Test
    void settingsReturnsCanUpload3dFlag() throws Exception {
        RestaurantBrandingDto branding = new RestaurantBrandingDto(7L, "Bistro A", "bistro-a", "Seasonal", null, null, null,
                null, null, null, "bistro", null, null, "bistro.tok", null, "gold", "compact", "EUR", "€");
        when(brandingService.getSettings(eq(TENANT)))
                .thenReturn(new StudioSettingsDto(branding, "O", "a@x.com", RestaurantStatus.ACTIVE, 0, 0, 0, true));

        mvc.perform(get("/api/v1/studio/settings").with(TestFixtures.staff(7L, "bistro-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canUpload3d").value(true));
    }
}
