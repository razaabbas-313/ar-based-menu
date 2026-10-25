package com.atelier.media.web;

import com.atelier.media.dto.PublicMenuDto;
import com.atelier.media.dto.RestaurantBrandingDto;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.model.DishCategory;
import com.atelier.media.security.SecurityConfig;
import com.atelier.media.security.TokenService;
import com.atelier.media.service.AnalyticsService;
import com.atelier.media.service.PublicMenuService;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({RestaurantController.class, PublicMenuController.class})
@Import({SecurityConfig.class, TokenService.class})
@ActiveProfiles("test")
class RestaurantControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PublicMenuService publicMenuService;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private com.atelier.media.security.ClientIpResolver clientIpResolver;


    static PublicMenuDto menuA() {
        RestaurantBrandingDto branding = new RestaurantBrandingDto(1L, "Bistro A", "bistro-a", "Seasonal", "Bio",
                "Tue-Sun", "https://cdn/logo.webp", "https://cdn/banner.webp", null, "+33612345678", "bistro", null,
                "https://bistro.example", "bistro.tok", "https://maps.app.goo.gl/abc", "emerald", "cards", "EUR", "€");
        return new PublicMenuDto(branding, List.of(
                TestFixtures.menuItem(1L, DishCategory.DESSERTS, true),
                TestFixtures.menuItem(2L, DishCategory.DESSERTS, false),
                TestFixtures.menuItem(3L, DishCategory.DRINKS, true)));
    }

    @Test
    void publicMenuIsAnonymousCacheableAndBranded() throws Exception {
        when(publicMenuService.getMenu("bistro-a")).thenReturn(menuA());

        mvc.perform(get("/api/v1/public/menu/bistro-a"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=15, public, stale-while-revalidate=60"))
                .andExpect(jsonPath("$.restaurant.themeAccent").value("emerald"))
                .andExpect(jsonPath("$.restaurant.whatsappNumber").value("+33612345678"))
                .andExpect(jsonPath("$.restaurant.email").doesNotExist())
                .andExpect(jsonPath("$.restaurant.storageUsedBytes").doesNotExist())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].dietaryTags[0]").value("vegan"));
    }

    @Test
    void anonymousRequestWithPreviewParamReturnsNormalGuestResponse() throws Exception {
        when(publicMenuService.getMenu("bistro-a")).thenReturn(menuA());

        mvc.perform(get("/api/v1/public/menu/bistro-a").param("preview", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=15, public, stale-while-revalidate=60"))
                .andExpect(jsonPath("$.restaurant.themeAccent").value("emerald"))
                .andExpect(jsonPath("$.items", hasSize(3)));

        verify(publicMenuService).getMenu("bistro-a");
        verify(publicMenuService, never()).getStaffPreview(anyString());
    }

    @Test
    void menuOnlyQueriesTheRequestedRestaurant() throws Exception {
        when(publicMenuService.getMenu("bistro-a")).thenReturn(menuA());

        mvc.perform(get("/api/v1/public/menu/Bistro-A")).andExpect(status().isOk());

        verify(publicMenuService).getMenu("bistro-a");
        verifyNoMoreInteractions(publicMenuService);
    }

    @Test
    void legacyMenuRouteStillWorksWithFilters() throws Exception {
        when(publicMenuService.getMenu("bistro-a")).thenReturn(menuA());

        mvc.perform(get("/api/v1/restaurants/bistro-a/menu").param("category", "DESSERTS").param("arOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(1));
    }

    @Test
    void unknownOrHiddenRestaurantIs404() throws Exception {
        when(publicMenuService.getMenu("nowhere")).thenThrow(new RestaurantNotFoundException("nowhere"));

        mvc.perform(get("/api/v1/public/menu/nowhere")).andExpect(status().isNotFound());
    }

    @Test
    void malformedSlugNeverHitsTheService() throws Exception {
        mvc.perform(get("/api/v1/public/menu/-bad-")).andExpect(status().isNotFound());

        verify(publicMenuService, never()).getMenu(anyString());
    }

    @Test
    void invalidCategoryIs400() throws Exception {
        mvc.perform(get("/api/v1/restaurants/bistro-a/menu").param("category", "SOUPS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thereIsNoPublicDirectoryOfRestaurants() throws Exception {
        // Menus are private: reachable only by their direct /r/{slug} link, never listed.
        mvc.perform(get("/api/v1/restaurants")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/restaurants").with(TestFixtures.staff(1L, "bistro-a"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public/menu")).andExpect(status().isUnauthorized());
        verifyNoInteractions(publicMenuService);
    }

    @Test
    void publicMenuExposesTheNewSocialLinks() throws Exception {
        when(publicMenuService.getMenu("bistro-a")).thenReturn(menuA());

        mvc.perform(get("/api/v1/public/menu/bistro-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurant.tiktokHandle").value("bistro.tok"))
                .andExpect(jsonPath("$.restaurant.googleMapsUrl").value("https://maps.app.goo.gl/abc"));
    }
}
