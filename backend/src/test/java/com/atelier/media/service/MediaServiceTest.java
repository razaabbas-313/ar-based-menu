package com.atelier.media.service;

import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.PresignRequestDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.UploadConfirmDto;
import com.atelier.media.exception.AccountSuspendedException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.InvalidMediaException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.UploadVerificationException;
import com.atelier.media.model.DishCategory;
import com.atelier.media.model.Media;
import com.atelier.media.model.MediaType;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.atelier.media.support.ServiceHarness.A;
import static com.atelier.media.support.ServiceHarness.B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Photo pipeline: tenant scoping, S3 verification, usage counters and the stale-upload reaper. */
class MediaServiceTest {

    private static final Tenant TENANT_A = new Tenant(A, "bistro-a");

    private final ServiceHarness h = new ServiceHarness();
    private final MediaService service = new MediaService(h.mediaRepository, h.categoryRepository, h.restaurantRepository, h.restaurantAccess,
            h.storage, h.props, h.tx, h.menuCache);

    @AfterEach
    void tearDown() {
        h.close();
    }

    @Test
    void presignScopesKeyAndRowToTheAuthenticatedRestaurant() {
        when(h.mediaRepository.save(any(Media.class))).thenAnswer(inv -> {
            Media m = inv.getArgument(0);
            m.setId(10L);
            return m;
        });

        PresignResponseDto res = service.generatePresignedUploadUrl(TENANT_A, photo("IMG_2041.webp", List.of("Vegan", "spicy")));

        assertThat(res.objectKey()).startsWith("restaurant-atelier/bistro-a/image-2d/").endsWith(".webp");
        assertThat(res.uploadUrl()).contains(res.objectKey()).contains("X-Amz-Signature");
        assertThat(res.expiresInSeconds()).isEqualTo(300L);
        ArgumentCaptor<Media> saved = ArgumentCaptor.forClass(Media.class);
        verify(h.mediaRepository).save(saved.capture());
        assertThat(saved.getValue().getRestaurantId()).isEqualTo(A);
        assertThat(saved.getValue().getDietaryTags()).isEqualTo("vegan,spicy");
        assertThat(saved.getValue().getAvailable()).isTrue();
        assertThat(saved.getValue().hasModel()).isFalse();
    }

    @Test
    void presignUsesTheDatabaseSlugNotTheTokenSlug() {
        PresignResponseDto res = service.generatePresignedUploadUrl(new Tenant(A, "bistro-b"), photo("dish.webp", null));

        assertThat(res.objectKey()).startsWith("restaurant-atelier/bistro-a/");
    }

    @Test
    void newDishesMustStartFromAPhoto() {
        PresignRequestDto glb = new PresignRequestDto("dome.glb", "model/gltf-binary", "Dome", new BigDecimal("9"),
                DishCategory.DESSERTS, MediaType.IMAGE_2D, null, 100L, "table", null, null, null);

        assertThatThrownBy(() -> service.generatePresignedUploadUrl(TENANT_A, glb))
                .isInstanceOf(InvalidMediaException.class).hasMessageContaining("photo first");
    }

    @Test
    void unknownDietaryTagIsAFieldError() {
        assertThatThrownBy(() -> service.generatePresignedUploadUrl(TENANT_A, photo("d.webp", List.of("keto-ish"))))
                .isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void suspendedRestaurantCannotUpload() {
        Restaurant suspended = TestFixtures.restaurant(A, "bistro-a");
        suspended.setStatus(RestaurantStatus.SUSPENDED);
        when(h.restaurantRepository.findById(A)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.generatePresignedUploadUrl(TENANT_A, photo("d.webp", null)))
                .isInstanceOf(AccountSuspendedException.class);
        verify(h.mediaRepository, never()).save(any());
    }

    @Test
    void restaurantACannotConfirmRestaurantBsUpload() {
        Media bUpload = TestFixtures.media(55L, B, "bistro-b", UploadStatus.PRESIGNED);
        when(h.mediaRepository.findByS3Key(bUpload.getS3Key())).thenReturn(Optional.of(bUpload));
        when(h.mediaRepository.findByS3KeyAndRestaurantId(bUpload.getS3Key(), B)).thenReturn(Optional.of(bUpload));

        assertThatThrownBy(() -> service.confirmUpload(TENANT_A, confirm(bUpload.getS3Key())))
                .isInstanceOf(MediaNotFoundException.class);

        verify(h.s3Client, never()).headObject(any(HeadObjectRequest.class));
        verify(h.restaurantRepository, never()).adjustUsage(anyLong(), anyLong(), anyInt(), anyInt());
        assertThat(bUpload.getUploadStatus()).isEqualTo(UploadStatus.PRESIGNED);
        assertThat(h.cached("bistro-b")).isTrue();
    }

    @Test
    void confirmVerifiesWithS3CountsTheDishAndEvictsOnlyItsOwnMenu() {
        Media aUpload = TestFixtures.media(56L, A, "bistro-a", UploadStatus.PRESIGNED);
        when(h.mediaRepository.findByS3KeyAndRestaurantId(aUpload.getS3Key(), A)).thenReturn(Optional.of(aUpload));
        when(h.mediaRepository.findByIdAndRestaurantId(56L, A)).thenReturn(Optional.of(aUpload));
        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(100L).eTag("\"abc123\"").build());

        MediaResponseDto dto = service.confirmUpload(TENANT_A, confirm(aUpload.getS3Key()));

        assertThat(dto.uploadStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(dto.etag()).isEqualTo("abc123");
        assertThat(dto.compressionSavedPercent()).isEqualTo(90);
        verify(h.restaurantRepository).adjustUsage(A, 100L, 1, 0);
        assertThat(h.cached("bistro-a")).isFalse();
        assertThat(h.cached("bistro-b")).isTrue();
    }

    @Test
    void confirmingTwiceDoesNotDoubleCount() {
        Media done = TestFixtures.media(57L, A, "bistro-a", UploadStatus.COMPLETED);
        when(h.mediaRepository.findByS3KeyAndRestaurantId(done.getS3Key(), A)).thenReturn(Optional.of(done));

        service.confirmUpload(TENANT_A, confirm(done.getS3Key()));

        verify(h.s3Client, never()).headObject(any(HeadObjectRequest.class));
        verify(h.restaurantRepository, never()).adjustUsage(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void sizeMismatchMarksFailedRemovesTheObjectAndCountsNothing() {
        Media aUpload = TestFixtures.media(58L, A, "bistro-a", UploadStatus.PRESIGNED);
        when(h.mediaRepository.findByS3KeyAndRestaurantId(aUpload.getS3Key(), A)).thenReturn(Optional.of(aUpload));
        when(h.mediaRepository.findById(58L)).thenReturn(Optional.of(aUpload));
        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(999_999L).build());

        assertThatThrownBy(() -> service.confirmUpload(TENANT_A, confirm(aUpload.getS3Key())))
                .isInstanceOf(UploadVerificationException.class);

        assertThat(aUpload.getUploadStatus()).isEqualTo(UploadStatus.FAILED);
        verify(h.s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(h.restaurantRepository, never()).adjustUsage(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void reaperCommitsBeforeCallingS3AndAlsoExpiresPendingModels() {
        Media stalePhoto = TestFixtures.media(90L, A, "bistro-a", UploadStatus.PRESIGNED);
        Media pendingModel = TestFixtures.media(91L, A, "bistro-a", UploadStatus.COMPLETED);
        pendingModel.setPendingModelS3Key("restaurant-atelier/bistro-a/model-3d/pending.glb");
        pendingModel.setPendingModelSizeBytes(10L);
        when(h.mediaRepository.findTop200ByUploadStatusAndCreatedAtBefore(eq(UploadStatus.PRESIGNED), any(Instant.class)))
                .thenReturn(List.of(stalePhoto));
        when(h.mediaRepository.findTop200ByPendingModelS3KeyIsNotNullAndPendingModelCreatedAtBefore(any(Instant.class)))
                .thenReturn(List.of(pendingModel));

        int expired = service.expireStalePresignedUploads();

        assertThat(expired).isEqualTo(2);
        assertThat(stalePhoto.getUploadStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(pendingModel.getPendingModelS3Key()).isNull();
        assertThat(pendingModel.getUploadStatus()).isEqualTo(UploadStatus.COMPLETED);
        InOrder order = inOrder(h.txManager, h.s3Client);
        order.verify(h.txManager, org.mockito.Mockito.times(3)).commit(any());
        order.verify(h.s3Client, org.mockito.Mockito.times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    private static PresignRequestDto photo(String filename, List<String> tags) {
        return new PresignRequestDto(filename, "image/webp", "Hokkaido Scallop Crudo", new BigDecimal("28.50"),
                DishCategory.STARTERS, MediaType.IMAGE_2D, 1_000L, 100L, "table", "Yuzu, finger lime", tags, null);
    }

    private static UploadConfirmDto confirm(String key) {
        return new UploadConfirmDto(key, "abc123", 100L, 1400, 1050, false);
    }
}
