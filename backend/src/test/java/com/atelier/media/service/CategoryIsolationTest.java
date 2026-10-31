package com.atelier.media.service;

import com.atelier.media.dto.DishUpdateDto;
import com.atelier.media.dto.PresignRequestDto;
import com.atelier.media.dto.admin.AdminModelApproveDto;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.model.*;
import com.atelier.media.repository.ModelRequestPendingKeyRepository;
import com.atelier.media.repository.ModelRequestRepository;
import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.security.Tenant;
import com.atelier.media.support.ServiceHarness;
import com.atelier.media.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CategoryIsolationTest {

    private final ServiceHarness h = new ServiceHarness();
    private final Tenant tenant = new Tenant(1L, "bistro-a");
    private final S3Storage storage = spy(h.storage);
    private final MediaService media = new MediaService(h.mediaRepository, h.categoryRepository,
            h.restaurantRepository, h.restaurantAccess, storage, h.props, h.tx, h.menuCache);
    private final DishService dishes = new DishService(h.mediaRepository, h.restaurantRepository,
            h.restaurantAccess, h.categoryRepository, storage, h.props, h.tx, h.menuCache);

    @AfterEach
    void close() { h.close(); }

    @ParameterizedTest
    @ValueSource(longs = {20L, 999L})
    void foreignOrMissingCategoryCannotCreateDishOrSignUpload(long categoryId) {
        assertThatThrownBy(() -> media.generatePresignedUploadUrl(tenant, photo(categoryId)))
                .isInstanceOf(InvalidFieldException.class).hasMessage("Category not found.");
        verify(h.categoryRepository).findByIdAndRestaurantId(categoryId, 1L);
        verify(h.mediaRepository, never()).save(any());
        verify(h.restaurantRepository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(storage, h.s3Client);
    }

    @ParameterizedTest
    @ValueSource(longs = {20L, 999L})
    void foreignOrMissingCategoryCannotMutateDish(long categoryId) {
        Media dish = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        String previousTitle = dish.getDishTitle();
        when(h.mediaRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(dish));
        assertThatThrownBy(() -> dishes.updateDish(tenant, 10L, update(categoryId)))
                .isInstanceOf(InvalidFieldException.class).hasMessage("Category not found.");
        assertThat(dish.getDishTitle()).isEqualTo(previousTitle);
        assertThat(dish.getCategoryId()).isNull();
        verify(h.mediaRepository, never()).save(any());
        assertThat(h.cached("bistro-a")).isTrue();
        verifyNoInteractions(storage, h.s3Client);
    }

    @Test
    void ownCategoryCanBeAssignedDuringCreationAndUpdate() {
        Category own = Category.builder().id(20L).restaurantId(1L).name("Own category").build();
        when(h.categoryRepository.findByIdAndRestaurantId(20L, 1L)).thenReturn(Optional.of(own));
        media.generatePresignedUploadUrl(tenant, photo(20L));
        verify(h.mediaRepository).save(argThat(d -> Long.valueOf(20L).equals(d.getCategoryId())
                && Long.valueOf(1L).equals(d.getRestaurantId())));

        Media dish = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(dish));
        var updated = dishes.updateDish(tenant, 10L, update(20L));
        assertThat(updated.categoryId()).isEqualTo(20L);
        assertThat(updated.categoryName()).isEqualTo("Own category");
        verify(h.categoryRepository, never()).findById(anyLong());
    }

    @Test
    void legacyForeignAssociationDoesNotLeakCategoryNameInDishResponse() {
        Media dish = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        dish.setCategoryId(20L);
        when(h.mediaRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(dish));
        var updated = dishes.updateDish(tenant, 10L, update(null));
        assertThat(updated.categoryName()).isNull();
        verify(h.categoryRepository).findByIdAndRestaurantId(20L, 1L);
        verify(h.categoryRepository, never()).findById(anyLong());
    }

    @Test
    void legacyForeignAssociationDoesNotLeakCategoryNameInConciergeResponse() {
        var requests = mock(ModelRequestRepository.class);
        var pending = mock(ModelRequestPendingKeyRepository.class);
        var concierge = new ModelRequestService(h.mediaRepository, h.restaurantRepository, h.categoryRepository,
                requests, pending, storage, h.menuCache, new ObjectMapper(), h.props);
        Media dish = TestFixtures.media(10L, 1L, "bistro-a", UploadStatus.COMPLETED);
        dish.setCategoryId(20L);
        when(h.mediaRepository.findById(10L)).thenReturn(Optional.of(dish));
        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(100L).contentType("model/gltf-binary").build());
        var response = concierge.approveModel(10L,
                new AdminModelApproveDto("restaurant-atelier/bistro-a/models/twin.glb", null, "table"),
                new AdminPrincipal(1L, "owner@platform.test"));
        assertThat(response.categoryName()).isNull();
        verify(h.categoryRepository).findByIdAndRestaurantId(20L, 1L);
        verify(h.categoryRepository, never()).findById(anyLong());
    }

    private static PresignRequestDto photo(Long categoryId) {
        return new PresignRequestDto("dish.webp", "image/webp", "Dish", BigDecimal.TEN,
                DishCategory.MAINS, MediaType.IMAGE_2D, 1000L, 100L, "table", null, List.of(), true, categoryId);
    }

    private static DishUpdateDto update(Long categoryId) {
        return new DishUpdateDto("Updated", BigDecimal.TEN, DishCategory.MAINS, null, List.of(),
                true, "table", false, categoryId);
    }
}
