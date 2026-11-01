package com.atelier.media.web;

import com.atelier.media.model.*;
import com.atelier.media.repository.*;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual HTTP security and current-account checks. Storage/publication is mocked here. */
@WebMvcTest(ProductStudioController.class)
@Import({SecurityConfig.class,TokenService.class,AdminAccess.class,RestaurantAccess.class})
@ActiveProfiles("test")
class ProductStudioControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ProductStudioService studio;
    @MockBean ModelHistoryService history;
    @MockBean ModelRequestService models;
    @MockBean RestaurantRepository restaurants;
    @MockBean PlatformUserRepository users;
    @MockBean MediaRepository media;
    Restaurant restaurant;
    PlatformUser admin;
    Media dish;
    private static final String MODEL_BODY="{\"filename\":\"synthetic.glb\",\"contentType\":\"model/gltf-binary\",\"uploadSizeBytes\":500,\"arPlacement\":\"table\"}";
    private static final String CONFIRM_BODY="{\"s3Key\":\"restaurant-atelier/test/models/synthetic.glb\",\"arPlacement\":\"table\"}";

    @BeforeEach void seed(){
        restaurant=TestFixtures.restaurant(7,"model-bistro");dish=TestFixtures.media(32,7,"model-bistro",UploadStatus.COMPLETED);
        admin=PlatformUser.builder().id(1L).email("operator@platform.test").displayName("Model operator").passwordHash("synthetic-placeholder").mustChangePassword(false).build();
        when(restaurants.findById(7L)).thenReturn(Optional.of(restaurant));when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(media.findByIdAndRestaurantId(32L,7L)).thenReturn(Optional.of(dish));
    }
    @ParameterizedTest @CsvSource({
            "GET,/api/v1/studio/dishes/32/model-history",
            "GET,/api/v1/studio/appearance",
            "PUT,/api/v1/studio/appearance/draft",
            "POST,/api/v1/studio/appearance/publish",
            "POST,/api/v1/studio/appearance/discard",
            "GET,/api/v1/studio/reviews",
            "PUT,/api/v1/studio/reviews",
            "GET,/api/v1/admin/restaurants/7/dishes/32/model-history",
            "POST,/api/v1/admin/restaurants/7/dishes/32/model/presign",
            "POST,/api/v1/admin/restaurants/7/dishes/32/model/confirm"})
    void protectedProductRoutesRejectAnonymous(String method,String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method),path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());verifyNoInteractions(studio,history,models,media);
    }
    @Test void historyUsesAuthenticatedOwnerAndKeysetCursorNotSuppliedTenant() throws Exception {
        when(history.list(7,32,17)).thenReturn(List.of(Map.of("version_number",2,"state","CURRENT")));
        mvc.perform(get("/api/v1/studio/dishes/32/model-history?after=17&restaurantId=99").with(TestFixtures.staff(7,"model-bistro")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$[0].version_number").value(2));
        verify(history).list(7,32,17);verify(media).findByIdAndRestaurantId(32L,7L);
    }
    @Test void ownerCannotReadForeignDishHistory() throws Exception {
        mvc.perform(get("/api/v1/studio/dishes/99/model-history").with(TestFixtures.staff(7,"model-bistro")))
                .andExpect(status().isBadRequest());verifyNoInteractions(history);
    }
    @Test void revokedOwnerCannotReadHistory() throws Exception {
        restaurant.incrementTokenVersion();
        mvc.perform(get("/api/v1/studio/dishes/32/model-history").with(TestFixtures.staff(7,"model-bistro")))
                .andExpect(status().isUnauthorized());verifyNoInteractions(media,history);
    }
    @ParameterizedTest @CsvSource({"presign","confirm"})
    void administratorMustSelectMatchingRestaurantAndDish(String operation) throws Exception {
        mvc.perform(post("/api/v1/admin/restaurants/99/dishes/32/model/"+operation).with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(operation.equals("presign")?MODEL_BODY:CONFIRM_BODY))
                .andExpect(status().isBadRequest());verifyNoInteractions(models);
    }
    @Test void currentAdministratorCanUseExistingNoRequestPresignPipeline() throws Exception {
        mvc.perform(post("/api/v1/admin/restaurants/7/dishes/32/model/presign").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(MODEL_BODY))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(models).presignModelUpload(eq(32L),argThat(dto->dto.uploadSizeBytes()==500L),argThat(actor->actor.adminId()==1L));
    }
    @Test void currentAdministratorCanConfirmThroughExistingPublicationPipeline() throws Exception {
        mvc.perform(post("/api/v1/admin/restaurants/7/dishes/32/model/confirm").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_BODY))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(models).approveModel(eq(32L),argThat(dto->dto.s3Key().endsWith("synthetic.glb")),argThat(actor->actor.adminId()==1L));
    }
    @Test void revokedAdministratorCannotReachPublicationPipeline() throws Exception {
        admin.incrementTokenVersion();
        mvc.perform(post("/api/v1/admin/restaurants/7/dishes/32/model/presign").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(MODEL_BODY))
                .andExpect(status().isUnauthorized());verifyNoInteractions(media,models);
    }
    @Test void ownerSessionCannotUseAdminPublicationRoute() throws Exception {
        mvc.perform(post("/api/v1/admin/restaurants/7/dishes/32/model/presign").with(TestFixtures.staff(7,"model-bistro"))
                        .contentType(MediaType.APPLICATION_JSON).content(MODEL_BODY))
                .andExpect(status().isForbidden());verifyNoInteractions(models,media);
    }
    @Test void publicAppearanceIsAnonymousAndNotCached() throws Exception {
        when(studio.publicAppearance("model-bistro")).thenReturn(Map.of("appearance",Map.of("preset","LEGACY")));
        mvc.perform(get("/api/v1/public/appearance/model-bistro"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.appearance.preset").value("LEGACY"));
    }
    @Test void anonymousPublishedBrandAssetBrokerRedirectsWithoutCachingOrReferrer() throws Exception {
        String destination="https://example.test/synthetic-private-brand-get";
        when(studio.publicAssetUrl("synthetic-published-asset")).thenReturn(destination);
        mvc.perform(get("/api/v1/public/brand-assets/synthetic-published-asset"))
                    .andExpect(status().isFound()).andExpect(header().string("Location",destination))
                    .andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(header().string("Referrer-Policy","no-referrer"));
        verify(studio).publicAssetUrl("synthetic-published-asset");
    }
    @Test void unpublishedBrandAssetRejectionDoesNotRedirect() throws Exception {
        when(studio.publicAssetUrl("synthetic-draft-asset")).thenThrow(new com.atelier.media.exception.InvalidFieldException("image","Image is not currently published."));
        mvc.perform(get("/api/v1/public/brand-assets/synthetic-draft-asset"))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Location"));
    }
}
