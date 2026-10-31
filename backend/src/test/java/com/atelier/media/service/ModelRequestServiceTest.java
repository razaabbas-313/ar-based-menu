package com.atelier.media.service;

import com.atelier.media.dto.ModelRequestHistoryDto;
import com.atelier.media.model.Media;
import com.atelier.media.model.ModelRequest;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.repository.CategoryRepository;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.ModelRequestRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.support.ServiceHarness;
import com.atelier.media.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.ModelRequestConfirmDto;
import com.atelier.media.dto.ModelRequestItemDto;
import com.atelier.media.dto.admin.AdminModelApproveDto;
import com.atelier.media.exception.ConflictException;
import com.atelier.media.exception.InvalidMediaException;
import com.atelier.media.model.ModelRequestPendingKey;
import com.atelier.media.repository.ModelRequestPendingKeyRepository;
import com.atelier.media.security.AdminPrincipal;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelRequestServiceTest {

    private final ServiceHarness h = new ServiceHarness();
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ModelRequestRepository modelRequestRepository = mock(ModelRequestRepository.class);
    private final ModelRequestPendingKeyRepository pendingKeyRepository = mock(ModelRequestPendingKeyRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtelierProperties props = TestFixtures.atelierProperties();

    private final ModelRequestService service = new ModelRequestService(
            h.mediaRepository,
            h.restaurantRepository,
            categoryRepository,
            modelRequestRepository,
            pendingKeyRepository,
            h.storage,
            h.menuCache,
            objectMapper,
            props, h.tx, h.restaurantAccess, mock(AdminAccess.class), mock(DurableCleanup.class)
    );

    @AfterEach
    void tearDown() {
        h.close();
    }

    private Media eligibleReferenceDish(String json) {
        Media dish=TestFixtures.media(10L,1L,"bistro-a",UploadStatus.COMPLETED);
        dish.setModelRequestStatus("COMPLETED");dish.setModelCompletedAt(Instant.now().minusSeconds(15*86400));dish.setModelRequestPhotos(json);
        when(h.mediaRepository.findExpiredReferenceCandidates(org.mockito.ArgumentMatchers.anyLong(),any(Instant.class),any(Pageable.class))).thenReturn(List.of(dish));
        when(h.mediaRepository.findByIdAndRestaurantIdForUpdate(10L,1L)).thenReturn(Optional.of(dish));return dish;
    }
    @Test void referenceExpiryReleasesMetadataWithoutSynchronousS3() {
        Media dish=eligibleReferenceDish("[{\"s3Key\":\"a\"}]");
        assertThat(service.cleanupReferencePhotos()).isOne();assertThat(dish.getModelRequestPhotos()).isEqualTo("[]");
        verify(h.s3Client,org.mockito.Mockito.never()).deleteObjects(any(software.amazon.awssdk.services.s3.model.DeleteObjectsRequest.class));
    }
    @Test void activeHistoryPreventsReferenceRelease() {
        Media dish=eligibleReferenceDish("[{\"s3Key\":\"a\"}]");
        when(modelRequestRepository.findByDishIdOrderByRequestedAtDesc(10L)).thenReturn(List.of(ModelRequest.builder().status("PENDING").build()));
        assertThat(service.cleanupReferencePhotos()).isZero();assertThat(dish.getModelRequestPhotos()).contains("a");
    }
    @Test void malformedReferenceMetadataIsNotErased() {
        Media dish=eligibleReferenceDish("invalid-json");
        assertThat(service.cleanupReferencePhotos()).isZero();
        assertThat(dish.getModelRequestPhotos()).isEqualTo("invalid-json");
    }
    @Test void referenceEntriesWithoutKeysArePreservedForInvestigation() {
        Media dish=eligibleReferenceDish("[{\"slot\":\"front\"}]");
        assertThat(service.cleanupReferencePhotos()).isZero();assertThat(dish.getModelRequestPhotos()).contains("front");
    }

    @Test
    void getAllHistoryReturnsDecoratedHistoryWithDishCodeAndSlug() {
        Restaurant r1 = TestFixtures.restaurant(1L, "bistro-a");
        Media d1 = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        d1.setDishCode("DSH-0001");
        d1.setDishTitle("Truffle Pasta");

        ModelRequest req1 = ModelRequest.builder()
                .id(100L)
                .restaurantId(1L)
                .dishId(10L)
                .status("COMPLETED")
                .requestType("NEW")
                .requestedAt(Instant.now().minusSeconds(3600))
                .completedAt(Instant.now())
                .completedModelKey("restaurant-atelier/bistro-a/models/twin-1.glb")
                .build();

        when(modelRequestRepository.findFiltered(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req1), PageRequest.of(0, 50), 1));
        when(h.restaurantRepository.findAllById(List.of(1L))).thenReturn(List.of(r1));
        when(h.mediaRepository.findAllById(List.of(10L))).thenReturn(List.of(d1));

        var historyPage = service.getAllHistory(null);

        assertThat(historyPage.items()).hasSize(1);
        ModelRequestHistoryDto dto = historyPage.items().get(0);
        assertThat(dto.restaurantSlug()).isEqualTo("bistro-a");
        assertThat(dto.dishCode()).isEqualTo("DSH-0001");
        assertThat(dto.dishTitle()).isEqualTo("Truffle Pasta");
        assertThat(dto.status()).isEqualTo("COMPLETED");
        assertThat(dto.completedModelUrl()).contains("twin-1.glb");
    }

    @Test
    void getAllHistoryFiltersByRestaurantIdWhenSpecified() {
        Restaurant r2 = TestFixtures.restaurant(2L, "bistro-b");
        Media d2 = TestFixtures.media(20L, 2L, "bistro-b", UploadStatus.COMPLETED);
        d2.setDishCode("DSH-0002");
        d2.setDishTitle("Wagyu Steak");

        ModelRequest req2 = ModelRequest.builder()
                .id(200L)
                .restaurantId(2L)
                .dishId(20L)
                .status("IN_PRODUCTION")
                .requestType("REPLACEMENT")
                .requestedAt(Instant.now())
                .build();

        when(modelRequestRepository.findFiltered(eq(2L), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req2), PageRequest.of(0, 50), 1));
        when(h.restaurantRepository.findAllById(List.of(2L))).thenReturn(List.of(r2));
        when(h.mediaRepository.findAllById(List.of(20L))).thenReturn(List.of(d2));

        var historyPage = service.getAllHistory(2L);

        assertThat(historyPage.items()).hasSize(1);
        ModelRequestHistoryDto dto = historyPage.items().get(0);
        assertThat(dto.restaurantSlug()).isEqualTo("bistro-b");
        assertThat(dto.dishCode()).isEqualTo("DSH-0002");
        assertThat(dto.status()).isEqualTo("IN_PRODUCTION");
        assertThat(dto.requestType()).isEqualTo("REPLACEMENT");
    }

    @Test
    void presignPhotosRejectsWrongContentType() {
        ModelRequestItemDto badItem = new ModelRequestItemDto("front", "photo.pdf", "application/pdf", 1000L);
        assertThatThrownBy(() -> service.presignPhotos(1L, 10L, List.of(badItem)))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("Unsupported photo type");
    }

    @Test
    void presignPhotosRejectsOversize() {
        ModelRequestItemDto oversizeItem = new ModelRequestItemDto("front", "photo.png", "image/png", 20L * 1024 * 1024);
        assertThatThrownBy(() -> service.presignPhotos(1L, 10L, List.of(oversizeItem)))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void presignPhotosRejectsDuplicateOrInvalidSlots() {
        ModelRequestItemDto item1 = new ModelRequestItemDto("front", "p1.png", "image/png", 1000L);
        ModelRequestItemDto item2 = new ModelRequestItemDto("front", "p2.png", "image/png", 1000L);
        assertThatThrownBy(() -> service.presignPhotos(1L, 10L, List.of(item1, item2)))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("Duplicate slot");

        ModelRequestItemDto invalidSlot = new ModelRequestItemDto("underneath", "p3.png", "image/png", 1000L);
        assertThatThrownBy(() -> service.presignPhotos(1L, 10L, List.of(invalidSlot)))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void presignPhotosRejectsDuplicateActiveRequest() {
        Restaurant r = TestFixtures.restaurant(1L, "bistro-a");
        Media d = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        d.setModelRequestStatus("PENDING");
        when(h.restaurantRepository.findById(1L)).thenReturn(Optional.of(r));
        when(h.mediaRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(d));

        ModelRequestItemDto item = new ModelRequestItemDto("front", "p1.png", "image/png", 1000L);
        assertThatThrownBy(() -> service.presignPhotos(1L, 10L, List.of(item)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmRequestRejectsForeignTenantKey() {
        Restaurant r = TestFixtures.restaurant(1L, "bistro-a");
        Media d = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findByIdAndRestaurantIdForUpdate(10L, 1L)).thenReturn(Optional.of(d));
        when(h.mediaRepository.findByIdAndRestaurantId(10L,1L)).thenReturn(Optional.of(d));
        when(h.restaurantRepository.findById(1L)).thenReturn(Optional.of(r));

        ModelRequestConfirmDto.UploadedPhotoRef foreignPhoto = new ModelRequestConfirmDto.UploadedPhotoRef(
                "front", "restaurant-atelier-private/evil-tenant/requests/10/ref-front.webp", "https://s3/url"
        );
        ModelRequestConfirmDto confirmDto = new ModelRequestConfirmDto(List.of(foreignPhoto), "notes");

        assertThatThrownBy(() -> service.confirmRequest(new com.atelier.media.security.Tenant(1L,"bistro-a",false,1), 10L, confirmDto))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("not currently issued");
    }

    @Test
    void confirmRequestRejectsUnissuedKey() {
        Restaurant r = TestFixtures.restaurant(1L, "bistro-a");
        Media d = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findByIdAndRestaurantIdForUpdate(10L, 1L)).thenReturn(Optional.of(d));
        when(h.mediaRepository.findByIdAndRestaurantId(10L,1L)).thenReturn(Optional.of(d));
        when(h.restaurantRepository.findById(1L)).thenReturn(Optional.of(r));
        when(pendingKeyRepository.findByRestaurantIdAndDishId(1L, 10L)).thenReturn(List.of()); // No issued keys!

        ModelRequestConfirmDto.UploadedPhotoRef unissuedPhoto = new ModelRequestConfirmDto.UploadedPhotoRef(
                "front", "restaurant-atelier-private/bistro-a/requests/10/ref-front-xyz.webp", "https://s3/url"
        );
        ModelRequestConfirmDto confirmDto = new ModelRequestConfirmDto(List.of(unissuedPhoto), "notes");

        assertThatThrownBy(() -> service.confirmRequest(new com.atelier.media.security.Tenant(1L,"bistro-a",false,1), 10L, confirmDto))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("not currently issued");
    }

    @Test
    void presignPhotosUsesPrivatePrefix() {
        Restaurant r = TestFixtures.restaurant(1L, "bistro-a");
        Media d = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        when(h.restaurantRepository.findById(1L)).thenReturn(Optional.of(r));
        when(h.mediaRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(d));

        var res = service.presignPhotos(1L, 10L, List.of(new ModelRequestItemDto("front", "p.webp", "image/webp", 1000L)));

        assertThat(res).hasSize(1);
        assertThat(res.get(0).objectKey()).startsWith("restaurant-atelier-private/bistro-a/requests/10/");
    }

    @Test
    void presignPhotosAcceptsBothStandardAndCompoundSlotNames() {
        Restaurant r = TestFixtures.restaurant(1L, "bistro-a");
        Media d = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        when(h.restaurantRepository.findById(1L)).thenReturn(Optional.of(r));
        when(h.mediaRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(d));

        var items = List.of(
                new ModelRequestItemDto("FRONT_45", "front.webp", "image/webp", 1000L),
                new ModelRequestItemDto("top", "top.webp", "image/webp", 1000L),
                new ModelRequestItemDto("LEFT_SIDE", "left.webp", "image/webp", 1000L)
        );
        var res = service.presignPhotos(1L, 10L, items);

        assertThat(res).hasSize(3);
        assertThat(res.get(0).slot()).isEqualTo("FRONT_45");
        assertThat(res.get(0).objectKey()).contains("ref-front-");
        assertThat(res.get(1).slot()).isEqualTo("top");
        assertThat(res.get(1).objectKey()).contains("ref-top-");
        assertThat(res.get(2).slot()).isEqualTo("LEFT_SIDE");
        assertThat(res.get(2).objectKey()).contains("ref-left-");
    }

    @Test
    void pendingRequestsAndHistoryGeneratePresignedGetUrls() {
        Restaurant r = TestFixtures.restaurant(1L, "bistro-a");
        Media d = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        d.setModelRequestStatus("PENDING");
        d.setModelRequestPhotos("[{\"slot\":\"front\",\"s3Key\":\"restaurant-atelier-private/bistro-a/requests/10/ref-front.webp\"}]");
        when(h.mediaRepository.findByModelRequestStatusOrderByModelRequestedAtDesc(eq("PENDING"), any(Pageable.class))).thenReturn(List.of(d));
        when(h.restaurantRepository.findAllById(List.of(1L))).thenReturn(List.of(r));

        var pending = service.getPendingRequests();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).photos()).hasSize(1);
        assertThat(pending.get(0).photos().get(0).s3Url())
                .contains("restaurant-atelier-private/bistro-a/requests/10/ref-front.webp")
                .contains("X-Amz-Signature");

        ModelRequest req = ModelRequest.builder()
                .id(100L)
                .restaurantId(1L)
                .dishId(10L)
                .status("COMPLETED")
                .requestType("NEW")
                .referencePhotos("[{\"slot\":\"front\",\"s3Key\":\"restaurant-atelier-private/bistro-a/requests/10/ref-front.webp\"}]")
                .requestedAt(Instant.now())
                .build();
        when(modelRequestRepository.findFiltered(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(req), PageRequest.of(0, 50), 1));
        when(h.mediaRepository.findAllById(List.of(10L))).thenReturn(List.of(d));

        var history = service.getAllHistory(null);
        assertThat(history.items()).hasSize(1);
        assertThat(history.items().get(0).referencePhotos())
                .contains("X-Amz-Signature");
    }

    @Test
    void requestPage0Size10Returns10ItemsAndCorrectPageMetadata() {
        List<ModelRequest> tenRequests = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            tenRequests.add(ModelRequest.builder()
                    .id(i)
                    .restaurantId(1L)
                    .dishId(i)
                    .status("COMPLETED")
                    .requestType("NEW")
                    .requestedAt(Instant.now())
                    .build());
        }
        when(modelRequestRepository.findFiltered(eq(null), eq(null), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(tenRequests, PageRequest.of(0, 10), 25));

        var result = service.getAllHistory(null, null, 0, 10);

        assertThat(result.items()).hasSize(10);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(25);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void statusFilterReturnsOnlyMatchingRows() {
        ModelRequest pendingReq = ModelRequest.builder()
                .id(101L)
                .restaurantId(1L)
                .dishId(10L)
                .status("PENDING")
                .requestType("NEW")
                .requestedAt(Instant.now())
                .build();
        when(modelRequestRepository.findFiltered(eq(null), eq("PENDING"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pendingReq), PageRequest.of(0, 50), 1));

        var result = service.getAllHistory(null, "PENDING", 0, 50);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).status()).isEqualTo("PENDING");
        verify(modelRequestRepository).findFiltered(eq(null), eq("PENDING"), any(Pageable.class));
    }

    @Test
    void bucketPolicyExcludesPrivatePrefix() throws Exception {
        String policy = java.nio.file.Files.readString(java.nio.file.Path.of("../infra/s3-public-read-policy.json"));
        // Private access relies on absence of an anonymous grant, not a Deny blocking the signer.
        assertThat(policy).contains("YOUR_BUCKET_NAME/restaurant-atelier/*");
        assertThat(policy).doesNotContain("restaurant-atelier-private/");
        assertThat(policy).doesNotContain("\"Effect\": \"Deny\"");
    }

    @Test
    void approveModelRejectsKeyFromAnotherTenant() {
        Restaurant r = TestFixtures.restaurant(1L, "bistro-a");
        Media d = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findById(10L)).thenReturn(Optional.of(d));
        when(h.restaurantRepository.findById(1L)).thenReturn(Optional.of(r));

        AdminModelApproveDto foreignKeyDto = new AdminModelApproveDto(
                "restaurant-atelier/bistro-b/models/twin-1.glb", "etag1", "table"
        );
        AdminPrincipal admin = new AdminPrincipal(99L, "admin@atelier.local");

        assertThatThrownBy(() -> service.approveModel(10L, foreignKeyDto, admin))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("no longer issued");
    }

    @Test
    void approveModelRejectsOversizedModel() {
        Restaurant r = TestFixtures.restaurant(1L, "bistro-a");
        Media d = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findById(10L)).thenReturn(Optional.of(d));
        when(h.restaurantRepository.findById(1L)).thenReturn(Optional.of(r));

        String key = "restaurant-atelier/bistro-a/models/twin-oversize.glb";
        d.setPendingModelS3Key(key);
        AdminModelApproveDto approveDto = new AdminModelApproveDto(key, "etag", "table");
        AdminPrincipal admin = new AdminPrincipal(99L, "admin@atelier.local");

        // Set S3 HEAD response to 30 MB (cap is 25 MB)
        when(h.s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(30L * 1024 * 1024).contentType("model/gltf-binary").build());

        assertThatThrownBy(() -> service.approveModel(10L, approveDto, admin))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("limit");
    }
}
