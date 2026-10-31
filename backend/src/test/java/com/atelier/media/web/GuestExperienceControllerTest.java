package com.atelier.media.web;

import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.PlatformUserRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.*;
import com.atelier.media.service.*;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP security chain; persistence and guest business decisions are mocked. */
@WebMvcTest(GuestExperienceController.class)
@Import({SecurityConfig.class,TokenService.class,AdminAccess.class,RestaurantAccess.class})
@ActiveProfiles("test")
class GuestExperienceControllerTest {
    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @MockBean RestaurantRepository restaurants;
    @MockBean PlatformUserRepository admins;
    @MockBean GuestExperienceService service;
    @MockBean ClientIpResolver ips;
    Restaurant restaurant;
    @BeforeEach void seed(){restaurant=TestFixtures.restaurant(7L,"guest-bistro");when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant));when(ips.resolveClientIp(any())).thenReturn("198.51.100.10");}

    @ParameterizedTest @CsvSource({"GET,/api/v1/studio/experience","PUT,/api/v1/studio/experience","GET,/api/v1/studio/tables","POST,/api/v1/studio/tables","PUT,/api/v1/studio/tables/4","POST,/api/v1/studio/tables/4/rotate"})
    void ownerRoutesRejectAnonymousRequests(String method,String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method),path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test void ownerPaginationUsesAuthenticatedTenantAndNoStore() throws Exception {
        when(service.tables(any(),eq(200L))).thenReturn(List.of(Map.of("id",201L,"name","Table 201")));
        mvc.perform(get("/api/v1/studio/tables?after=200&restaurantId=99").with(TestFixtures.staff(7L,"guest-bistro")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$[0].id").value(201));
        verify(service).tables(argThat(t->t.restaurantId()==7L),eq(200L));
    }
    @Test void serviceCurrentAccountRejectionMapsToUnauthorized() throws Exception {
        when(service.settings(any())).thenThrow(new com.atelier.media.exception.InvalidTokenException("Restaurant account is no longer available."));
        mvc.perform(get("/api/v1/studio/experience").with(TestFixtures.staff(7L,"guest-bistro"))).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @CsvSource({"context,tableToken","order-preparations,items","waiter-requests,tableToken"})
    void guestHandoffRoutesAreAnonymousAndNeverCached(String route,String field) throws Exception {
        when(service.publicContext(eq("guest-bistro"),any())).thenReturn(Map.of("orderingAvailable",false));
        when(service.prepareOrder(eq("guest-bistro"),any(),any())).thenReturn(Map.of("status","PREPARED_REQUEST"));
        when(service.waiter(eq("guest-bistro"),any(),any())).thenReturn(Map.of("message","Press Send in WhatsApp"));
        mvc.perform(post("/api/v1/public/"+route+"/guest-bistro").contentType(MediaType.APPLICATION_JSON).content("{\""+field+"\":null}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void malformedGuestJsonProducesClientErrorWithoutCallingService() throws Exception {
        mvc.perform(post("/api/v1/public/order-preparations/guest-bistro").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void fractionalQuantityIsRejectedAtHttpBoundaryBeforeService() throws Exception {
        mvc.perform(post("/api/v1/public/order-preparations/guest-bistro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"dishId\":1,\"quantity\":1.5,\"expectedUnitPrice\":\"18.00\",\"note\":null}],\"notes\":null,\"tableToken\":null}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void stringQuantityIsRejectedAtHttpBoundaryBeforeService() throws Exception {
        mvc.perform(post("/api/v1/public/order-preparations/guest-bistro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"dishId\":1,\"quantity\":\"2\",\"expectedUnitPrice\":\"18.00\",\"note\":null}],\"notes\":null,\"tableToken\":null}"))
                .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
}
