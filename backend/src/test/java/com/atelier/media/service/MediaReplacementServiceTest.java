package com.atelier.media.service;

import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.ReplaceAssetKind;
import com.atelier.media.dto.ReplaceConfirmDto;
import com.atelier.media.dto.ReplacePresignDto;
import com.atelier.media.exception.InvalidMediaException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.PasswordChangeRequiredException;
import com.atelier.media.exception.UploadVerificationException;
import com.atelier.media.model.Media;
import com.atelier.media.model.MediaType;
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

/** In-place photo / model replacement: replace-presign + replace-confirm (v4). */
class MediaReplacementServiceTest {

    private static final Tenant TENANT_A = new Tenant(A, "bistro-a");

    private final ServiceHarness h = new ServiceHarness();
    private final DishService dishService = new DishService(h.mediaRepository, h.restaurantRepository, h.restaurantAccess,
            h.storage, h.props, h.tx, h.menuCache);
    private final MediaReplacementService service = new MediaReplacementService(h.mediaRepository, h.restaurantRepository,
            h.restaurantAccess, h.storage, h.props, h.tx, h.menuCache, dishService);

    @AfterEach
    void tearDown() {
        h.close();
    }

    // ---------------------------------------------------------------- replace-presign

    @Test
    void presignSignsANewKeyInTheTenantsFolderAndKeepsTheLivePhoto() {
        Media dish = TestFixtures.media(20L, A, "bistro-a", UploadStatus.COMPLETED);
        String livePhoto = dish.getS3Key();
        when(h.mediaRepository.findByIdAndRestaurantId(20L, A)).thenReturn(Optional.of(dish));

        PresignResponseDto presign = service.presign(TENANT_A, 20L, photo(380_000L));

        assertThat(presign.objectKey()).startsWith("restaurant-atelier/bistro-a/image-2d/").endsWith(".webp");
        assertThat(presign.objectKey()).isNotEqualTo(livePhoto);
        assertThat(presign.uploadUrl()).contains("X-Amz-Signature=").contains("bistro-a");
        assertThat(presign.mediaId()).isEqualTo(20L);
        assertThat(dish.getS3Key()).isEqualTo(livePhoto);
        assertThat(dish.getPendingImageS3Key()).isEqualTo(presign.objectKey());
        assertThat(dish.getPendingImageSizeBytes()).isEqualTo(380_000L);
        assertThat(dish.getPendingImageOriginalSizeBytes()).isEqualTo(8_200_000L);
        assertThat(dish.getPendingImageContentType()).isEqualTo("image/webp");
        verify(h.s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        assertThat(h.cached("bistro-a")).isTrue(); // nothing changed for guests yet
    }

    @Test
    void anotherTenantsDishIsNotFoundAndNothingIsSigned() {
        Media bDish = TestFixtures.media(77L, B, "bistro-b", UploadStatus.COMPLETED);
        when(h.mediaRepository.findByIdAndRestaurantId(77L, B)).thenReturn(Optional.of(bDish));

        assertThatThrownBy(() -> service.presign(TENANT_A, 77L, photo(1_000L))).isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.confirm(TENANT_A, 77L, confirm("restaurant-atelier/bistro-b/image-2d/x.webp")))
                .isInstanceOf(MediaNotFoundException.class);
        verify(h.mediaRepository, never()).save(any());
        verify(h.s3Client, never()).headObject(any(HeadObjectRequest.class));
        assertThat(bDish.getPendingImageS3Key()).isNull();
    }

    @Test
    void presignRejectsNonImagesAndOversizedPhotos() {
        Media dish = TestFixtures.media(21L, A, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findByIdAndRestaurantId(21L, A)).thenReturn(Optional.of(dish));

        assertThatThrownBy(() -> service.presign(TENANT_A, 21L,
                new ReplacePresignDto(ReplaceAssetKind.PHOTO, "clip.mp4", "video/mp4", 1_000L, null, null)))
                .isInstanceOf(InvalidMediaException.class);
        assertThatThrownBy(() -> service.presign(TENANT_A, 21L, photo(h.props.maxImageBytes() + 1)))
                .isInstanceOf(InvalidMediaException.class);
        assertThat(dish.getPendingImageS3Key()).isNull();
    }

    @Test
    void aSecondPresignDiscardsTheEarlierUnconfirmedUpload() {
        Media dish = TestFixtures.media(22L, A, "bistro-a", UploadStatus.COMPLETED);
        dish.setPendingImageS3Key("restaurant-atelier/bistro-a/image-2d/abandoned.webp");
        when(h.mediaRepository.findByIdAndRestaurantId(22L, A)).thenReturn(Optional.of(dish));

        service.presign(TENANT_A, 22L, photo(2_000L));

        ArgumentCaptor<DeleteObjectRequest> deleted = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(h.s3Client).deleteObject(deleted.capture());
        assertThat(deleted.getValue().key()).isEqualTo("restaurant-atelier/bistro-a/image-2d/abandoned.webp");
    }

    @Test
    void ownersWithATemporaryPasswordCannotReplaceMedia() {
        Restaurant locked = TestFixtures.restaurant(A, "bistro-a");
        locked.setMustChangePassword(true);
        when(h.restaurantRepository.findById(A)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.presign(TENANT_A, 20L, photo(1_000L))).isInstanceOf(PasswordChangeRequiredException.class);
    }

    // ---------------------------------------------------------------- replace-confirm

    @Test
    void confirmVerifiesWithHeadSwapsAdjustsStorageThenDeletesTheOldObjectAfterCommit() {
        Media dish = pendingDish(23L, 380_000L);
        String oldKey = dish.getS3Key();
        String newKey = dish.getPendingImageS3Key();
        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(380_000L).eTag("\"etag-new\"").build());

        MediaResponseDto dto = service.confirm(TENANT_A, 23L,
                new ReplaceConfirmDto(ReplaceAssetKind.PHOTO, newKey, "etag-new", 1400, 1050));

        assertThat(dto.s3Key()).isEqualTo(newKey);
        assertThat(dto.imageUrl()).endsWith(newKey);
        assertThat(dish.getFileSizeBytes()).isEqualTo(380_000L);
        assertThat(dish.getOriginalSizeBytes()).isEqualTo(8_200_000L);
        assertThat(dish.getEtag()).isEqualTo("etag-new");
        assertThat(dish.getWidthPx()).isEqualTo(1400);
        assertThat(dish.getHeightPx()).isEqualTo(1050);
        assertThat(dish.getContentType()).isEqualTo("image/webp");
        assertThat(dish.getPendingImageS3Key()).isNull();

        ArgumentCaptor<DeleteObjectRequest> deleted = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        InOrder order = inOrder(h.s3Client, h.restaurantRepository, h.txManager);
        order.verify(h.s3Client).headObject(any(HeadObjectRequest.class)); // outside the transaction
        order.verify(h.txManager).getTransaction(any());
        order.verify(h.restaurantRepository).adjustUsage(A, 380_000L - 100L, 0, 0);
        order.verify(h.txManager).commit(any());
        order.verify(h.s3Client).deleteObject(deleted.capture());
        assertThat(deleted.getValue().key()).isEqualTo(oldKey);

        // Only this restaurant's menu entry is evicted.
        assertThat(h.cached("bistro-a")).isFalse();
        assertThat(h.cached("bistro-b")).isTrue();
    }

    @Test
    void sizeMismatchDiscardsTheNewObjectAndKeepsTheLivePhoto() {
        Media dish = pendingDish(24L, 380_000L);
        String live = dish.getS3Key();
        String newKey = dish.getPendingImageS3Key();
        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(999L).build());

        assertThatThrownBy(() -> service.confirm(TENANT_A, 24L, confirm(newKey)))
                .isInstanceOf(UploadVerificationException.class)
                .hasMessageContaining("Size mismatch");

        assertThat(dish.getS3Key()).isEqualTo(live);
        assertThat(dish.getPendingImageS3Key()).isNull();
        ArgumentCaptor<DeleteObjectRequest> deleted = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(h.s3Client).deleteObject(deleted.capture());
        assertThat(deleted.getValue().key()).isEqualTo(newKey);
        verify(h.restaurantRepository, never()).adjustUsage(anyLong(), anyLong(), anyInt(), anyInt());
        assertThat(h.cached("bistro-a")).isTrue();
    }

    @Test
    void etagMismatchIsRejected() {
        Media dish = pendingDish(25L, 380_000L);
        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(380_000L).eTag("\"someone-else\"").build());

        assertThatThrownBy(() -> service.confirm(TENANT_A, 25L,
                new ReplaceConfirmDto(ReplaceAssetKind.PHOTO, dish.getPendingImageS3Key(), "mine", null, null)))
                .isInstanceOf(UploadVerificationException.class)
                .hasMessageContaining("ETag");
        verify(h.restaurantRepository, never()).adjustUsage(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void confirmRejectsKeysThatWereNeverPresignedForThisDish() {
        Media dish = pendingDish(26L, 380_000L);

        assertThatThrownBy(() -> service.confirm(TENANT_A, 26L, confirm("restaurant-atelier/bistro-b/image-2d/stolen.webp")))
                .isInstanceOf(UploadVerificationException.class);
        verify(h.s3Client, never()).headObject(any(HeadObjectRequest.class));
        assertThat(dish.getPendingImageS3Key()).isNotNull();
    }

    @Test
    void confirmIsIdempotentOnRetry() {
        Media dish = TestFixtures.media(27L, A, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findByIdAndRestaurantId(27L, A)).thenReturn(Optional.of(dish));

        MediaResponseDto dto = service.confirm(TENANT_A, 27L, confirm(dish.getS3Key()));

        assertThat(dto.id()).isEqualTo(27L);
        verify(h.s3Client, never()).headObject(any(HeadObjectRequest.class));
        verify(h.s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void aLegacy3dOnlyDishGainsAPhotoAndKeepsItsModel() {
        Media legacy = TestFixtures.media(28L, A, "bistro-a", UploadStatus.COMPLETED);
        String glb = "restaurant-atelier/bistro-a/model-3d/28.glb";
        legacy.setS3Key(glb);
        legacy.setContentType("model/gltf-binary");
        legacy.setMediaType(MediaType.MODEL_3D);
        legacy.setFileSizeBytes(5_000L);
        legacy.setModelS3Key(glb);
        legacy.setModelFileSizeBytes(5_000L);
        when(h.mediaRepository.findByIdAndRestaurantId(28L, A)).thenReturn(Optional.of(legacy));
        PresignResponseDto presign = service.presign(TENANT_A, 28L, photo(400_000L));
        assertThat(presign.objectKey()).contains("/image-2d/");
        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(400_000L).build());

        MediaResponseDto dto = service.confirm(TENANT_A, 28L, confirm(presign.objectKey()));

        assertThat(legacy.isLegacyModelOnly()).isFalse();
        assertThat(legacy.getMediaType()).isEqualTo(MediaType.IMAGE_2D);
        assertThat(legacy.getModelS3Key()).isEqualTo(glb);
        assertThat(dto.is3d()).isTrue();
        verify(h.restaurantRepository).adjustUsage(A, 400_000L, 0, 0);
        // The .glb is still the dish's 3D model, so it must not be deleted.
        verify(h.s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void modelReplacementUsesThe3dTrack() {
        Media dish = TestFixtures.mediaWithModel(29L, A, "bistro-a");
        when(h.mediaRepository.findByIdAndRestaurantId(29L, A)).thenReturn(Optional.of(dish));

        PresignResponseDto presign = service.presign(TENANT_A, 29L,
                new ReplacePresignDto(ReplaceAssetKind.MODEL, "dome-v2.glb", "model/gltf-binary", 8_000L, null, "floor"));

        assertThat(presign.objectKey()).startsWith("restaurant-atelier/bistro-a/model-3d/").endsWith(".glb");
        assertThat(dish.getPendingModelS3Key()).isEqualTo(presign.objectKey());
        assertThat(dish.getArPlacement()).isEqualTo("floor");
        assertThat(dish.getPendingImageS3Key()).isNull();

        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(8_000L).build());
        service.confirm(TENANT_A, 29L, new ReplaceConfirmDto(ReplaceAssetKind.MODEL, presign.objectKey(), null, null, null));

        assertThat(dish.getModelS3Key()).isEqualTo(presign.objectKey());
        verify(h.restaurantRepository).adjustUsage(A, 3_000L, 0, 0);
        verify(h.s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class)); // the replaced .glb
    }

    // ---------------------------------------------------------------- helpers

    private Media pendingDish(long id, long size) {
        Media dish = TestFixtures.media(id, A, "bistro-a", UploadStatus.COMPLETED);
        dish.setPendingImageS3Key("restaurant-atelier/bistro-a/image-2d/replacement-" + id + ".webp");
        dish.setPendingImageSizeBytes(size);
        dish.setPendingImageOriginalSizeBytes(8_200_000L);
        dish.setPendingImageContentType("image/webp");
        when(h.mediaRepository.findByIdAndRestaurantId(id, A)).thenReturn(Optional.of(dish));
        return dish;
    }

    private static ReplacePresignDto photo(long size) {
        return new ReplacePresignDto(ReplaceAssetKind.PHOTO, "new-plating.webp", "image/webp", size, 8_200_000L, null);
    }

    private static ReplaceConfirmDto confirm(String key) {
        return new ReplaceConfirmDto(ReplaceAssetKind.PHOTO, key, null, 1400, 1050);
    }
}
