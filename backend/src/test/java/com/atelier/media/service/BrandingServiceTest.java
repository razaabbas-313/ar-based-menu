package com.atelier.media.service;

import com.atelier.media.dto.BrandingAssetConfirmDto;
import com.atelier.media.dto.BrandingAssetPresignDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.StudioSettingsDto;
import com.atelier.media.dto.StudioSettingsUpdateDto;
import com.atelier.media.exception.InvalidMediaException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.UploadVerificationException;
import com.atelier.media.model.Restaurant;
import com.atelier.media.security.Tenant;
import com.atelier.media.support.ServiceHarness;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.util.Optional;

import static com.atelier.media.support.ServiceHarness.A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrandingServiceTest {

    private static final Tenant TENANT_A = new Tenant(A, "bistro-a");

    private final ServiceHarness h = new ServiceHarness();
    private final BrandingService service = new BrandingService(h.restaurantRepository, h.restaurantAccess,
            h.storage, h.props, h.tx, h.menuCache);
    private Restaurant a;

    @BeforeEach
    void setUp() {
        a = TestFixtures.restaurant(A, "bistro-a");
        a.setStorageUsedBytes(10_000L);
        when(h.restaurantRepository.findById(A)).thenReturn(Optional.of(a));
        when(h.restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        h.close();
    }

    @Test
    void settingsUpdateNormalizesSocialLinksAndEvictsTheMenu() {
        StudioSettingsDto dto = service.updateSettings(TENANT_A, settings(false));

        assertThat(dto.branding().instagramHandle()).isEqualTo("bistro.lumiere");
        assertThat(dto.branding().themeAccent()).isEqualTo("emerald");
        assertThat(dto.branding().menuLayout()).isEqualTo("compact");
        assertThat(dto.branding().currencyCode()).isEqualTo("EUR");
        assertThat(dto.branding().tagline()).isNull(); // blank clears
        assertThat(h.cached("bistro-a")).isFalse();
        assertThat(h.cached("bistro-b")).isTrue();
    }

    @Test
    void logoPresignLandsInTheTenantsBrandingFolder() {
        PresignResponseDto res = service.presignAsset(TENANT_A, "logo",
                new BrandingAssetPresignDto("logo.webp", "image/webp", 20_000L));

        assertThat(res.objectKey()).startsWith("restaurant-atelier/bistro-a/branding/logo-").endsWith(".webp");
    }

    @Test
    void oversizedBannerIsRejectedBeforeSigning() {
        assertThatThrownBy(() -> service.presignAsset(TENANT_A, "banner",
                new BrandingAssetPresignDto("b.webp", "image/webp", 50L * 1024 * 1024)))
                .isInstanceOf(InvalidMediaException.class);
    }

    @Test
    void cannotConfirmAKeyOutsideTheTenantsBrandingFolder() {
        assertThatThrownBy(() -> service.confirmAsset(TENANT_A, "logo",
                new BrandingAssetConfirmDto("restaurant-atelier/bistro-b/branding/logo-x.webp")))
                .isInstanceOf(MediaNotFoundException.class);
        assertThatThrownBy(() -> service.confirmAsset(TENANT_A, "logo",
                new BrandingAssetConfirmDto("restaurant-atelier/bistro-a/branding/banner-x.webp")))
                .isInstanceOf(MediaNotFoundException.class);
        verify(h.s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void confirmingANewLogoReplacesTheOldOneAndAdjustsStorage() {
        a.setLogoS3Key("restaurant-atelier/bistro-a/branding/logo-old.webp");
        a.setLogoSizeBytes(4_000L);
        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(6_000L).contentType("image/webp").build());

        StudioSettingsDto dto = service.confirmAsset(TENANT_A, "logo",
                new BrandingAssetConfirmDto("restaurant-atelier/bistro-a/branding/logo-new.webp"));

        assertThat(dto.branding().logoUrl()).endsWith("logo-new.webp");
        verify(h.restaurantRepository).adjustUsage(A, 2_000L, 0, 0);
        verify(h.s3Client).deleteObject(DeleteObjectRequest.builder()
                .bucket("atelier-test-bucket").key("restaurant-atelier/bistro-a/branding/logo-old.webp").build());
    }

    @Test
    void nonImageUploadsAreDiscarded() {
        when(h.s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(6_000L).contentType("text/html").build());

        assertThatThrownBy(() -> service.confirmAsset(TENANT_A, "banner",
                new BrandingAssetConfirmDto("restaurant-atelier/bistro-a/branding/banner-x.webp")))
                .isInstanceOf(UploadVerificationException.class);
        verify(h.restaurantRepository, never()).adjustUsage(anyLong(), anyLong(), anyInt(), anyInt());
    }

    @Test
    void removingTheBannerFreesItsStorage() {
        a.setBannerS3Key("restaurant-atelier/bistro-a/branding/banner-1.webp");
        a.setBannerUrl("https://cdn/banner-1.webp");
        a.setBannerSizeBytes(300_000L);

        StudioSettingsDto dto = service.updateSettings(TENANT_A, settings(true));

        assertThat(dto.branding().bannerUrl()).isNull();
        verify(h.restaurantRepository).adjustUsage(A, -300_000L, 0, 0);
        verify(h.s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void getSettingsReflectsCanUpload3dPermission() {
        a.setCanUpload3d(true);
        StudioSettingsDto dto = service.getSettings(TENANT_A);
        assertThat(dto.canUpload3d()).isTrue();

        a.setCanUpload3d(false);
        StudioSettingsDto dto2 = service.getSettings(TENANT_A);
        assertThat(dto2.canUpload3d()).isFalse();
    }

    private static StudioSettingsUpdateDto settings(boolean removeBanner) {
        return new StudioSettingsUpdateDto("Bistro Lumière", "   ", "Seasonal tasting menus", "Tue-Sun 18:00-23:00",
                "+33 1 23 45 67 89", "+33 6 12 34 56 78", "@bistro.lumiere", "", "https://bistro.example",
                "@bistro.tok", "https://maps.app.goo.gl/AbC123", "emerald", "compact", "EUR", "€", false, removeBanner);
    }
}
