package com.atelier.media.security;


import com.atelier.media.exception.AccountSuspendedException;
import com.atelier.media.exception.InvalidTokenException;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.service.AdminService;
import com.atelier.media.service.DishService;
import com.atelier.media.service.RestaurantAccess;
import com.atelier.media.support.TestFixtures;
import com.atelier.media.web.AdminController;
import com.atelier.media.web.StudioController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminController.class, StudioController.class})
@Import({SecurityConfig.class, TokenService.class})
@ActiveProfiles("test")
class TokenSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private com.atelier.media.service.AdminAccess adminAccess;

    @MockBean
    private RestaurantAccess restaurantAccess;

    @MockBean
    private com.atelier.media.service.PublicMenuService publicMenuService;

    @MockBean
    private DishService dishService;

    @MockBean
    private com.atelier.media.service.ModelRequestService modelRequestService;

    @MockBean
    private com.atelier.media.service.BrandingService brandingService;

    @MockBean
    private com.atelier.media.service.CategoryService categoryService;

    @MockBean
    private com.atelier.media.service.AnalyticsService analyticsService;

    @MockBean
    private RestaurantRepository restaurantRepository;

    @MockBean
    private com.atelier.media.service.MenuCache menuCache;

    @Test
    void studioTokenCannotCallAdminEndpoints() throws Exception {
        mvc.perform(get("/api/v1/admin/restaurants").with(TestFixtures.staff(5L, "bistro-a")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Invalid audience"));
    }

    @Test
    void adminTokenCannotCallStudioEndpoints() throws Exception {
        mvc.perform(get("/api/v1/studio/dishes").with(TestFixtures.superAdmin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Invalid audience"));
    }

    @Test
    void tokenBecomesInvalidAfterPasswordChange() {
        RestaurantRepository repository = mock(RestaurantRepository.class);
        RestaurantAccess access = new RestaurantAccess(repository);

        Restaurant restaurant = TestFixtures.restaurant(10L, "bistro");
        restaurant.setTokenVersion(1);
        when(repository.findById(10L)).thenReturn(Optional.of(restaurant));

        // Token with version 1 is valid initially
        Tenant tenantV1 = new Tenant(10L, "bistro", false, 1);
        access.requireAccount(tenantV1);

        // Password change bumps token_version to 2
        restaurant.incrementTokenVersion(); // now 2

        // Token with version 1 is now rejected with InvalidTokenException
        assertThatThrownBy(() -> access.requireAccount(tenantV1))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("revoked");

        // Token with version 2 is accepted
        Tenant tenantV2 = new Tenant(10L, "bistro", false, 2);
        access.requireAccount(tenantV2);
    }

    @Test
    void suspendedRestaurantActiveTokenStopsWorking() {
        RestaurantRepository repository = mock(RestaurantRepository.class);
        RestaurantAccess access = new RestaurantAccess(repository);

        Restaurant restaurant = TestFixtures.restaurant(10L, "bistro");
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setTokenVersion(1);
        when(repository.findById(10L)).thenReturn(Optional.of(restaurant));

        Tenant tenant = new Tenant(10L, "bistro", false, 1);
        access.requireAccount(tenant);

        // Admin suspends restaurant -> status becomes SUSPENDED and token_version incremented
        restaurant.setStatus(RestaurantStatus.SUSPENDED);
        restaurant.incrementTokenVersion();

        assertThatThrownBy(() -> access.requireAccount(tenant))
                .isInstanceOf(InvalidTokenException.class);
    }
}
