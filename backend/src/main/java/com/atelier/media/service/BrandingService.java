package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.BrandingAssetConfirmDto;
import com.atelier.media.dto.BrandingAssetPresignDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.StudioSettingsDto;
import com.atelier.media.dto.StudioSettingsUpdateDto;
import com.atelier.media.exception.InvalidMediaException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.UploadVerificationException;
import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Brand & Menu Customizer: profile, social links, theme, currency, logo and banner. */
@Slf4j
@Service
public class BrandingService {
    private EntitlementService entitlements;
    @org.springframework.beans.factory.annotation.Autowired
    public void setEntitlements(EntitlementService entitlements) { this.entitlements=entitlements; }


    public enum AssetKind {
        LOGO, BANNER;

        public static AssetKind parse(String raw) {
            try {
                return AssetKind.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new MediaNotFoundException("Unknown branding asset '" + raw + "'. Use logo or banner.");
            }
        }

        String fileNamePrefix() {
            return name().toLowerCase(Locale.ROOT) + "-";
        }
    }

    private final RestaurantRepository restaurantRepository;
    private final RestaurantAccess restaurantAccess;
    private final S3Storage storage;
    private final AtelierProperties props;
    private final TransactionTemplate tx;
    private final MenuCache menuCache;

    public BrandingService(RestaurantRepository restaurantRepository,
                           RestaurantAccess restaurantAccess,
                           S3Storage storage,
                           AtelierProperties props,
                           TransactionTemplate tx,
                           MenuCache menuCache) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantAccess = restaurantAccess;
        this.storage = storage;
        this.props = props;
        this.tx = tx;
        this.menuCache = menuCache;
    }

    public StudioSettingsDto getSettings(Tenant tenant) {
        return StudioSettingsDto.from(restaurantAccess.requireStudioAccess(tenant));
    }

    public StudioSettingsDto updateSettings(Tenant tenant, StudioSettingsUpdateDto u) {
        restaurantAccess.requireStudioAccess(tenant);
        List<String> removedKeys = new ArrayList<>();
        String[] bucketSlug = new String[1];
        Restaurant saved = tx.execute(status -> {
            Restaurant r = restaurantRepository.findByIdForUpdate(tenant.restaurantId())
                    .orElseThrow(() -> new MediaNotFoundException("Restaurant not found"));
            restaurantAccess.requireStudioAccess(tenant);
            if(entitlements!=null)entitlements.requireFeature(tenant.restaurantId(),"branding.enabled");
            bucketSlug[0] = r.getSlug();
            r.setName(u.name().strip());
            r.setTagline(clean(u.tagline()));
            r.setBio(clean(u.bio()));
            r.setOpeningHours(clean(u.openingHours()));
            r.setPhoneNumber(clean(u.phoneNumber()));
            r.setWhatsappNumber(clean(u.whatsappNumber()));
            String ig = clean(u.instagramHandle());
            r.setInstagramHandle(ig == null ? null : ig.replaceFirst("^@", ""));
            r.setFacebookUrl(clean(u.facebookUrl()));
            r.setWebsiteUrl(clean(u.websiteUrl()));
            String tiktok = clean(u.tiktokHandle());
            r.setTiktokHandle(tiktok == null ? null : tiktok.replaceFirst("^@", ""));
            r.setGoogleMapsUrl(clean(u.googleMapsUrl()));
            if (u.themeAccent() != null) r.setThemeAccent(u.themeAccent());
            if (u.menuLayout() != null) r.setMenuLayout(u.menuLayout());
            if (u.currencyCode() != null) r.setCurrencyCode(u.currencyCode());
            if (u.currencySymbol() != null && !u.currencySymbol().isBlank()) r.setCurrencySymbol(u.currencySymbol().strip());
            long freed = 0;
            if (Boolean.TRUE.equals(u.removeLogo()) && r.getLogoS3Key() != null) {
                removedKeys.add(r.getLogoS3Key());
                freed += r.getLogoSizeBytes() == null ? 0 : r.getLogoSizeBytes();
                r.setLogoS3Key(null);
                r.setLogoUrl(null);
                r.setLogoSizeBytes(0L);
            }
            if (Boolean.TRUE.equals(u.removeBanner()) && r.getBannerS3Key() != null) {
                removedKeys.add(r.getBannerS3Key());
                freed += r.getBannerSizeBytes() == null ? 0 : r.getBannerSizeBytes();
                r.setBannerS3Key(null);
                r.setBannerUrl(null);
                r.setBannerSizeBytes(0L);
            }
            Restaurant updated = restaurantRepository.save(r);
            if (freed > 0) {
                restaurantRepository.adjustUsage(r.getId(), -freed, 0, 0);
                updated.setStorageUsedBytes(Math.max(0, updated.getStorageUsedBytes() - freed));
            }
            return updated;
        });
        menuCache.evict(bucketSlug[0]);
        return StudioSettingsDto.from(saved);
    }

    public PresignResponseDto presignAsset(Tenant tenant, String kindRaw, BrandingAssetPresignDto request) {
        AssetKind kind = AssetKind.parse(kindRaw);
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        if(entitlements!=null)entitlements.requireFeature(tenant.restaurantId(),"branding.enabled");
        storage.requireConfigured();
        String contentType = MediaRules.normalizeContentType(request.contentType());
        String ext = MediaRules.requireImage(contentType);
        MediaRules.requireCompatibleExtension(MediaRules.sanitizeFilename(request.filename()), contentType);
        MediaRules.requireMaxSize(request.uploadSizeBytes(), props.maxBrandingBytes(), "logos and banners");

        String key = storage.newKey(restaurant.getSlug(), "branding", kind.fileNamePrefix(), ext);
        S3Storage.PresignedUpload upload = storage.presignPut(key, contentType, request.uploadSizeBytes());
        return new PresignResponseDto(upload.url(), key, storage.publicUrl(key), null,
                upload.expiresAt(), upload.expiresInSeconds(), upload.requiredHeaders());
    }

    /** HEAD outside any transaction; then swap the asset and adjust storage in one short transaction. */
    public StudioSettingsDto confirmAsset(Tenant tenant, String kindRaw, BrandingAssetConfirmDto confirm) {
        AssetKind kind = AssetKind.parse(kindRaw);
        Restaurant restaurant = restaurantAccess.requireStudioAccess(tenant);
        String expectedPrefix = storage.tenantPrefix(restaurant.getSlug()) + "branding/" + kind.fileNamePrefix();
        if (!confirm.s3Key().startsWith(expectedPrefix) || confirm.s3Key().contains("..")) {
            throw new MediaNotFoundException("No " + kind.name().toLowerCase(Locale.ROOT) + " upload registered for key " + confirm.s3Key());
        }
        String alreadySet = kind == AssetKind.LOGO ? restaurant.getLogoS3Key() : restaurant.getBannerS3Key();
        if (confirm.s3Key().equals(alreadySet)) {
            return StudioSettingsDto.from(restaurant);
        }

        HeadObjectResponse head = storage.head(confirm.s3Key());
        long size = head.contentLength() == null ? 0 : head.contentLength();
        String headType = head.contentType() == null ? "" : MediaRules.normalizeContentType(head.contentType());
        if (!MediaRules.IMAGE_TYPES.containsKey(headType) || size <= 0 || size > props.maxBrandingBytes()) {
            throw new UploadVerificationException("The uploaded file is not a valid logo/banner image.");
        }

        String[] replaced = new String[1];
        Restaurant saved = tx.execute(status -> {
            Restaurant r = restaurantRepository.findByIdForUpdate(tenant.restaurantId())
                    .orElseThrow(() -> new MediaNotFoundException("Restaurant not found"));
            restaurantAccess.requireStudioAccess(tenant);
            if(entitlements!=null)entitlements.requireFeature(tenant.restaurantId(),"branding.enabled");
            long oldSize;
            if (kind == AssetKind.LOGO) {
                replaced[0] = r.getLogoS3Key();
                oldSize = r.getLogoSizeBytes() == null ? 0 : r.getLogoSizeBytes();
                r.setLogoS3Key(confirm.s3Key());
                r.setLogoUrl(storage.publicUrl(confirm.s3Key()));
                r.setLogoSizeBytes(size);
            } else {
                replaced[0] = r.getBannerS3Key();
                oldSize = r.getBannerSizeBytes() == null ? 0 : r.getBannerSizeBytes();
                r.setBannerS3Key(confirm.s3Key());
                r.setBannerUrl(storage.publicUrl(confirm.s3Key()));
                r.setBannerSizeBytes(size);
            }
            Restaurant updated = restaurantRepository.save(r);
            restaurantRepository.adjustUsage(r.getId(), size - oldSize, 0, 0);
            updated.setStorageUsedBytes(Math.max(0, updated.getStorageUsedBytes() + size - oldSize));
            return updated;
        });
        menuCache.evict(saved.getSlug());
        log.info("Branding {} updated restaurant={} key={} bytes={}", kind, saved.getSlug(), confirm.s3Key(), size);
        return StudioSettingsDto.from(saved);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String v = value.strip();
        return v.isEmpty() ? null : v;
    }

    /** Guard used by tests and the UI: rejects anything that is not an http(s) link. */
    static void requireHttpUrl(String value) {
        if (value != null && !value.matches("^https?://\\S+$")) {
            throw new InvalidMediaException("Links must start with http:// or https://");
        }
    }
}
