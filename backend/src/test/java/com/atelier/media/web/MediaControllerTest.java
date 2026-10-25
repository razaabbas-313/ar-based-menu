package com.atelier.media.web;

import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.model.DishCategory;
import com.atelier.media.security.SecurityConfig;
import com.atelier.media.security.Tenant;
import com.atelier.media.security.TokenService;
import com.atelier.media.service.MediaReplacementService;
import com.atelier.media.service.MediaService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
@Import({SecurityConfig.class, TokenService.class})
@ActiveProfiles("test")
class MediaControllerTest {

    private static final String PRESIGN_BODY = """
            {"filename":"IMG_2041.webp","contentType":"image/webp","dishTitle":"Scallop Crudo",
             "priceTag":28.50,"dishCategory":"STARTERS","mediaType":"IMAGE_2D",
             "originalSizeBytes":9640000,"uploadSizeBytes":402000,"arPlacement":"table",
             "description":"Yuzu, finger lime","dietaryTags":["gluten-free"],"available":true}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private MediaService mediaService;

    @MockBean
    private MediaReplacementService replacementService;

    private static final String REPLACE_PHOTO_BODY = """
            {"assetType":"PHOTO","filename":"new-plating.webp","contentType":"image/webp",
             "uploadSizeBytes":380000,"originalSizeBytes":8200000}
            """;

    // ---------------------------------------------------------------- in-place replacement (v4)

    @Test
    void replacePresignSignsANewKeyForTheTokensTenant() throws Exception {
        when(replacementService.presign(eq(new Tenant(7L, "bistro-a")), eq(12L), any())).thenReturn(new PresignResponseDto(
                "https://atelier-test-bucket.s3.amazonaws.com/restaurant-atelier/bistro-a/image-2d/new.webp?X-Amz-Signature=abc",
                "restaurant-atelier/bistro-a/image-2d/new.webp", "https://cdn/new.webp", 12L,
                Instant.now().plusSeconds(300), 300, Map.of("Content-Type", "image/webp")));

        mvc.perform(post("/api/v1/media/12/replace-presign").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(REPLACE_PHOTO_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.objectKey").value("restaurant-atelier/bistro-a/image-2d/new.webp"))
                .andExpect(jsonPath("$.mediaId").value(12))
                .andExpect(jsonPath("$.requiredHeaders['Content-Type']").value("image/webp"));
        verify(replacementService).presign(eq(new Tenant(7L, "bistro-a")), eq(12L), any());
    }

    @Test
    void replaceEndpointsRequireAFullStudioToken() throws Exception {
        mvc.perform(post("/api/v1/media/12/replace-presign").contentType(MediaType.APPLICATION_JSON).content(REPLACE_PHOTO_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/media/12/replace-presign").with(TestFixtures.setupStaff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(REPLACE_PHOTO_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/media/12/replace-confirm").with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetType\":\"PHOTO\",\"s3Key\":\"k\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(replacementService);
    }

    @Test
    void replacePresignValidatesTheRequest() throws Exception {
        mvc.perform(post("/api/v1/media/12/replace-presign").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetType\":\"VIDEO\",\"filename\":\"a.mp4\",\"contentType\":\"video/mp4\",\"uploadSizeBytes\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/media/12/replace-presign").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetType\":\"PHOTO\",\"filename\":\"a.webp\",\"contentType\":\"image/webp\",\"uploadSizeBytes\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.uploadSizeBytes").exists());
        verifyNoInteractions(replacementService);
    }

    @Test
    void replaceConfirmReturnsTheUpdatedDish() throws Exception {
        MediaResponseDto dish = TestFixtures.menuItem(12L, DishCategory.MAINS, true);
        when(replacementService.confirm(eq(new Tenant(7L, "bistro-a")), eq(12L), any())).thenReturn(dish);

        mvc.perform(post("/api/v1/media/12/replace-confirm").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetType\":\"PHOTO\",\"s3Key\":\"restaurant-atelier/bistro-a/image-2d/new.webp\",\"etag\":\"abc\",\"widthPx\":1400,\"heightPx\":1050}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12));
    }

    @Test
    void replaceConfirmNeedsTheKey() throws Exception {
        mvc.perform(post("/api/v1/media/12/replace-confirm").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assetType\":\"MODEL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.s3Key").exists());
        verifyNoInteractions(replacementService);
    }

    @Test
    void presignWithoutTokenIs401AndNeverReachesTheService() throws Exception {
        mvc.perform(post("/api/v1/media/presign").contentType(MediaType.APPLICATION_JSON).content(PRESIGN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Sign-in required"));

        verifyNoInteractions(mediaService);
    }

    @Test
    void presignWithGarbageTokenIs401() throws Exception {
        mvc.perform(post("/api/v1/media/presign")
                        .header("Authorization", "Bearer not.a.jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(PRESIGN_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(mediaService);
    }

    @Test
    void superAdminTokenCannotUseTheRestaurantStudio() throws Exception {
        mvc.perform(post("/api/v1/media/presign")
                        .with(TestFixtures.superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content(PRESIGN_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mediaService);
    }

    @Test
    void presignIsScopedToTheRestaurantInTheToken() throws Exception {
        when(mediaService.generatePresignedUploadUrl(any(Tenant.class), any()))
                .thenReturn(new PresignResponseDto("https://s3/put", "restaurant-atelier/bistro-a/image-2d/u.webp",
                        "https://cdn/u.webp", 10L, Instant.parse("2026-01-01T00:05:00Z"), 300L,
                        Map.of("content-type", "image/webp")));

        mvc.perform(post("/api/v1/media/presign").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(PRESIGN_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.objectKey").value("restaurant-atelier/bistro-a/image-2d/u.webp"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));

        verify(mediaService).generatePresignedUploadUrl(eq(new Tenant(7L, "bistro-a")), any());
    }

    @Test
    void realIssuedTokenIsAcceptedEndToEnd() throws Exception {
        String token = tokenService.issue(TestFixtures.restaurant(9L, "chez-marie")).value();
        when(mediaService.generatePresignedUploadUrl(any(Tenant.class), any()))
                .thenReturn(new PresignResponseDto("u", "k", "s", 1L, Instant.now(), 300L, Map.of()));

        mvc.perform(post("/api/v1/media/presign")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(PRESIGN_BODY))
                .andExpect(status().isCreated());

        verify(mediaService).generatePresignedUploadUrl(eq(new Tenant(9L, "chez-marie")), any());
    }

    @Test
    void presignValidationErrorsAreReportedPerField() throws Exception {
        String missingTitle = PRESIGN_BODY.replace("\"dishTitle\":\"Scallop Crudo\",", "");

        mvc.perform(post("/api/v1/media/presign").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(missingTitle))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.dishTitle").exists());

        verifyNoInteractions(mediaService);
    }

    @Test
    void overlongFilenameIsRejected() throws Exception {
        String longName = PRESIGN_BODY.replace("IMG_2041.webp", "a".repeat(300) + ".webp");

        mvc.perform(post("/api/v1/media/presign").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON).content(longName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.filename").exists());
    }

    @Test
    void confirmPassesTheTokenTenant() throws Exception {
        when(mediaService.confirmUpload(any(Tenant.class), any()))
                .thenReturn(TestFixtures.menuItem(10L, DishCategory.STARTERS, false));

        mvc.perform(post("/api/v1/media/confirm").with(TestFixtures.staff(7L, "bistro-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"s3Key\":\"restaurant-atelier/bistro-a/image-2d/u.webp\",\"finalSizeBytes\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadStatus").value("COMPLETED"));

        verify(mediaService).confirmUpload(eq(new Tenant(7L, "bistro-a")), any());
    }
}
