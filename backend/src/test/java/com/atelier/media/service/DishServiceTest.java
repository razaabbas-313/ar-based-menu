package com.atelier.media.service;

import com.atelier.media.dto.Attach3dRequestDto;
import com.atelier.media.dto.DishUpdateDto;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.ModelConfirmDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.exception.ConflictException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.UploadVerificationException;
import com.atelier.media.model.DishCategory;
import com.atelier.media.model.Media;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.security.Tenant;
import com.atelier.media.support.ServiceHarness;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.atelier.media.support.ServiceHarness.A;
import static com.atelier.media.support.ServiceHarness.B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Studio dish CRUD + the separate 3D track; Restaurant A can never touch Restaurant B's dishes. */
class DishServiceTest {

    private static final Tenant TENANT_A = new Tenant(A, "bistro-a");
    private static final Tenant TENANT_B = new Tenant(B, "bistro-b");

    private final ServiceHarness h = new ServiceHarness();
    private final DishService service = new DishService(h.mediaRepository, h.restaurantRepository, h.restaurantAccess,
            h.storage, h.props, h.tx, h.menuCache);

    @AfterEach
    void tearDown() {
        h.close();
    }

    // ---------------------------------------------------------------- isolation

    @Test
    void restaurantACannotEditDeleteOrAttachToRestaurantBsDish() {
        Media bDish = TestFixtures.media(77L, B, "bistro-b", UploadStatus.COMPLETED);
        when(h.mediaRepository.findById(77L)).thenReturn(Optional.of(bDish));
        when(h.mediaRepository.findByIdAndRestaurantId(77L, B)).thenReturn(Optional.of(bDish));

        assertThatThrownBy(() -> service.updateDish(TENANT_A, 77L, update("Hacked", true))).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.setAvailability(TENANT_A, 77L, false)).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.deleteDish(TENANT_A, 77L)).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.requestModelUpload(TENANT_A, 77L, glb(5000L))).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.confirmModelUpload(TENANT_A, 77L, new ModelConfirmDto("k", null))).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.removeModel(TENANT_A, 77L)).isInstanceOf(MediaNotFoundException.class);

        verify(h.mediaRepository, never()).save(any());
        verify(h.mediaRepository, never()).delete(any(Media.class));
        verify(h.s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(h.restaurantRepository, never()).adjustUsage(anyLong(), anyLong(), anyInt(), anyInt());
        assertThat(bDish.getDishTitle()).isEqualTo("Dish 77");
        assertThat(h.cached("bistro-b")).isTrue();
    }

    // ---------------------------------------------------------------- update / delete

    @Test
    void updateReplacesMetadataAndEvictsTheMenu() {
        Media dish = TestFixtures.media(10L, A, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findByIdAndRestaurantId(10L, A)).thenReturn(Optional.of(dish));

        MediaResponseDto dto = service.updateDish(TENANT_A, 10L, update("Saffron Risotto", false));

        assertThat(dto.dishTitle()).isEqualTo("Saffron Risotto");
        assertThat(dto.available()).isFalse();
        assertThat(dto.dietaryTags()).containsExactly("vegetarian", "gluten-free");
        assertThat(dto.dailySpecial()).isTrue();
        assertThat(h.cached("bistro-a")).isFalse();
    }

    @Test
    void deleteRemovesRowAndCountersInTheTransactionThenBothFilesFromS3() {
        Media dish = TestFixtures.mediaWithModel(11L, A, "bistro-a");
        when(h.mediaRepository.findByIdAndRestaurantId(11L, A)).thenReturn(Optional.of(dish));

        service.deleteDish(TENANT_A, 11L);

        InOrder order = inOrder(h.mediaRepository, h.restaurantRepository, h.txManager, h.s3Client);
        order.verify(h.mediaRepository).delete(dish);
        order.verify(h.restaurantRepository).adjustUsage(A, -5_100L, -1, -1);
        order.verify(h.txManager).commit(any());
        order.verify(h.s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
        assertThat(h.cached("bistro-a")).isFalse();
        assertThat(h.cached("bistro-b")).isTrue();
    }

    // ---------------------------------------------------------------- 3D track

    @Test
    void attach3dPresignsAPendingModelInTheTenantsModelFolder() {
        Media dish = TestFixtures.media(12L, A, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findByIdAndRestaurantId(12L, A)).thenReturn(Optional.of(dish));

        PresignResponseDto res = service.requestModelUpload(TENANT_A, 12L, glb(5_000L));

        assertThat(res.objectKey()).startsWith("restaurant-atelier/bistro-a/model-3d/").endsWith(".glb");
        assertThat(res.mediaId()).isEqualTo(12L);
        assertThat(dish.getPendingModelS3Key()).isEqualTo(res.objectKey());
        assertThat(dish.getPendingModelSizeBytes()).isEqualTo(5_000L);
        assertThat(dish.getS3Key()).endsWith(".webp"); // photo untouched
        verify(h.restaurantRepository, never()).adjustUsage(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void confirmFirstModelCountsItAndKeepsThePhoto() {
        Media dish = TestFixtures.media(13L, A, "bistro-a", UploadStatus.COMPLETED);
        dish.setPendingModelS3Key("restaurant-atelier/bistro-a/model-3d/new.glb");
        dish.setPendingModelSizeBytes(5_000L);
        when(h.mediaRepository.findByIdAndRestaurantId(13L, A)).thenReturn(Optional.of(dish));
        when(h.s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(5_000L).build());

        MediaResponseDto dto = service.confirmModelUpload(TENANT_A, 13L, new ModelConfirmDto("restaurant-atelier/bistro-a/model-3d/new.glb", null));

        assertThat(dto.is3d()).isTrue();
        assertThat(dto.hasArSupport()).isTrue();
        assertThat(dto.imageUrl()).endsWith(".webp");
        assertThat(dto.modelUrl()).endsWith("new.glb");
        assertThat(dish.getPendingModelS3Key()).isNull();
        verify(h.restaurantRepository).adjustUsage(A, 5_000L, 0, 1);
        verify(h.s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void replacingAModelAdjustsStorageByTheDifferenceAndDeletesTheOldFileAfterCommit() {
        Media dish = TestFixtures.mediaWithModel(14L, A, "bistro-a");
        String oldKey = dish.getModelS3Key();
        dish.setPendingModelS3Key("restaurant-atelier/bistro-a/model-3d/v2.glb");
        dish.setPendingModelSizeBytes(8_000L);
        when(h.mediaRepository.findByIdAndRestaurantId(14L, A)).thenReturn(Optional.of(dish));
        when(h.s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(8_000L).build());

        service.confirmModelUpload(TENANT_A, 14L, new ModelConfirmDto("restaurant-atelier/bistro-a/model-3d/v2.glb", null));

        ArgumentCaptor<DeleteObjectRequest> deleted = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        InOrder order = inOrder(h.restaurantRepository, h.txManager, h.s3Client);
        order.verify(h.restaurantRepository).adjustUsage(A, 3_000L, 0, 0);
        order.verify(h.txManager).commit(any());
        order.verify(h.s3Client).deleteObject(deleted.capture());
        assertThat(deleted.getValue().key()).isEqualTo(oldKey);
    }

    @Test
    void confirmRejectsKeysThatWereNeverPresignedForThisDish() {
        Media dish = TestFixtures.media(15L, A, "bistro-a", UploadStatus.COMPLETED);
        dish.setPendingModelS3Key("restaurant-atelier/bistro-a/model-3d/expected.glb");
        when(h.mediaRepository.findByIdAndRestaurantId(15L, A)).thenReturn(Optional.of(dish));

        assertThatThrownBy(() -> service.confirmModelUpload(TENANT_A, 15L,
                new ModelConfirmDto("restaurant-atelier/bistro-b/model-3d/stolen.glb", null)))
                .isInstanceOf(UploadVerificationException.class);
        verify(h.s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void removeModelKeepsThePhotoAndDecrementsCounters() {
        Media dish = TestFixtures.mediaWithModel(16L, A, "bistro-a");
        when(h.mediaRepository.findByIdAndRestaurantId(16L, A)).thenReturn(Optional.of(dish));

        MediaResponseDto dto = service.removeModel(TENANT_A, 16L);

        assertThat(dto.is3d()).isFalse();
        assertThat(dto.imageUrl()).endsWith(".webp");
        verify(h.restaurantRepository).adjustUsage(A, -5_000L, 0, -1);
        verify(h.s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void legacy3dOnlyDishesCannotAttachAnotherModel() {
        Media legacy = TestFixtures.media(17L, B, "bistro-b", UploadStatus.COMPLETED);
        legacy.setContentType("model/gltf-binary");
        legacy.setModelS3Key(legacy.getS3Key());
        when(h.mediaRepository.findByIdAndRestaurantId(17L, B)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.requestModelUpload(TENANT_B, 17L, glb(10L))).isInstanceOf(ConflictException.class);
    }

    @Test
    void requestModelUploadDeniedWhenRestaurantCannotUpload3d() {
        Restaurant restricted = TestFixtures.restaurant(B, "bistro-b");
        restricted.setCanUpload3d(false);
        when(h.restaurantRepository.findById(B)).thenReturn(Optional.of(restricted));

        assertThatThrownBy(() -> service.requestModelUpload(TENANT_B, 18L, glb(10L)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Direct 3D model uploads are not enabled");
    }

    @Test
    void confirmModelUploadDeniedWhenRestaurantCannotUpload3d() {
        Restaurant restricted = TestFixtures.restaurant(B, "bistro-b");
        restricted.setCanUpload3d(false);
        when(h.restaurantRepository.findById(B)).thenReturn(Optional.of(restricted));

        assertThatThrownBy(() -> service.confirmModelUpload(TENANT_B, 18L, new ModelConfirmDto("key.glb", null)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    private static DishUpdateDto update(String title, boolean available) {
        return new DishUpdateDto(title, new BigDecimal("24.00"), DishCategory.MAINS, "Carnaroli, saffron",
                List.of("Vegetarian", "gluten-free"), available, "table", true);
    }

    private static Attach3dRequestDto glb(long size) {
        return new Attach3dRequestDto("dome.glb", "model/gltf-binary", size, "table");
    }
}
