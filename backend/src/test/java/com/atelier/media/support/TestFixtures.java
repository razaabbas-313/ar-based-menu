package com.atelier.media.support;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.config.AwsProperties;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.model.DishCategory;
import com.atelier.media.model.Media;
import com.atelier.media.model.MediaType;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.security.Tenant;
import com.atelier.media.security.TokenService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Shared builders for unit and slice tests. */
public final class TestFixtures {

    public static final String JWT_SECRET = "test-only-jwt-secret-for-unit-tests-0123456789abcdef";
    public static final String ISSUER = "atelier-media-engine";

    private TestFixtures() {
    }

    public static AtelierProperties atelierProperties() {
        return atelierProperties(JWT_SECRET, ISSUER, Duration.ofHours(12));
    }

    public static AtelierProperties atelierProperties(String secret, String issuer, Duration ttl) {
        return new AtelierProperties(
                "restaurant-atelier",
                "restaurant-atelier-private",
                Duration.ofMinutes(5),
                Duration.ofMinutes(15),
                8L * 1024 * 1024,
                50L * 1024 * 1024,
                25L * 1024 * 1024,
                4L * 1024 * 1024,
                500,
                200,
                new AtelierProperties.Jwt(secret, issuer, ttl, Duration.ofHours(1)),
                new AtelierProperties.Cors(List.of("http://localhost:5173")),
                new AtelierProperties.Login(5, Duration.ofMinutes(15)),
                new AtelierProperties.SuperAdmin("admin@atelier.local", null, false),
                new AtelierProperties.PasswordReset(false),
                List.of());
    }

    public static AwsProperties awsProperties() {
        return new AwsProperties("us-east-1", null, null, new AwsProperties.S3("atelier-test-bucket", null));
    }

    public static Restaurant restaurant(long id, String slug) {
        return Restaurant.builder()
                .id(id)
                .name("Restaurant " + slug)
                .slug(slug)
                .ownerName("Owner " + id)
                .email(slug + "@example.com")
                .passwordHash("$2a$04$unused")
                .status(RestaurantStatus.ACTIVE)
                .canUpload3d(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    /** A completed photo dish (100-byte WebP, compressed from 1000 bytes), no 3D model. */
    public static Media media(long id, long restaurantId, String slug, UploadStatus status) {
        return Media.builder()
                .id(id)
                .restaurantId(restaurantId)
                .dishCode(String.format("DSH-%04d", id))
                .version(0L)
                .originalFileName("dish.webp")
                .storedFileName(id + ".webp")
                .s3Bucket("atelier-test-bucket")
                .s3Key("restaurant-atelier/" + slug + "/image-2d/" + id + ".webp")
                .s3Url("https://atelier-test-bucket.s3.us-east-1.amazonaws.com/restaurant-atelier/" + slug + "/image-2d/" + id + ".webp")
                .contentType("image/webp")
                .fileExtension("webp")
                .fileSizeBytes(100L)
                .originalSizeBytes(1_000L)
                .mediaType(MediaType.IMAGE_2D)
                .uploadStatus(status)
                .dishTitle("Dish " + id)
                .priceTag(new BigDecimal("18.00"))
                .dishCategory(DishCategory.MAINS)
                .hasArSupport(false)
                .arPlacement(Media.PLACEMENT_TABLE)
                .available(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .completedAt(status == UploadStatus.COMPLETED ? Instant.parse("2026-01-01T00:01:00Z") : null)
                .build();
    }

    /** Same as {@link #media} with a confirmed 5000-byte .glb attached. */
    public static Media mediaWithModel(long id, long restaurantId, String slug) {
        Media m = media(id, restaurantId, slug, UploadStatus.COMPLETED);
        m.setModelS3Key("restaurant-atelier/" + slug + "/model-3d/" + id + ".glb");
        m.setModelS3Url("https://cdn/" + id + ".glb");
        m.setModelFileSizeBytes(5_000L);
        m.setHasArSupport(true);
        return m;
    }

    public static MediaResponseDto menuItem(long id, DishCategory category, boolean is3d) {
        return new MediaResponseDto(id, "Dish " + id, new BigDecimal("12.00"), category,
                MediaType.IMAGE_2D, UploadStatus.COMPLETED, "image/webp", "webp", "f", "k" + id, "https://x/k" + id,
                is3d, is3d ? "https://x/m" + id + ".glb" : null, "https://x/k" + id,
                is3d, "table", "fixed", is3d ? "webxr scene-viewer quick-look" : null,
                null, null, 100L, null, null, "etag", Instant.now(), Instant.now(),
                "A dish", List.of("vegan"), true, false, is3d ? 5000L : 0L, false);
    }

    public static JwtRequestPostProcessor staff(long restaurantId, String slug) {
        return jwt()
                .jwt(j -> j.subject(String.valueOf(restaurantId))
                        .audience(List.of(TokenService.AUDIENCE_STUDIO))
                        .claim(TokenService.CLAIM_VERSION, 1)
                        .claim(Tenant.CLAIM_RESTAURANT_ID, restaurantId)
                        .claim(Tenant.CLAIM_SLUG, slug)
                        .claim(TokenService.ROLES_CLAIM, List.of(TokenService.ROLE_RESTAURANT)))
                .authorities(new SimpleGrantedAuthority(TokenService.ROLE_RESTAURANT));
    }

    /** A restaurant owner who signed in with a temporary password (v4 forced reset). */
    public static JwtRequestPostProcessor setupStaff(long restaurantId, String slug) {
        return jwt()
                .jwt(j -> j.subject(String.valueOf(restaurantId))
                        .audience(List.of(TokenService.AUDIENCE_STUDIO))
                        .claim(TokenService.CLAIM_VERSION, 1)
                        .claim(Tenant.CLAIM_RESTAURANT_ID, restaurantId)
                        .claim(Tenant.CLAIM_SLUG, slug)
                        .claim(TokenService.MUST_CHANGE_PASSWORD_CLAIM, true)
                        .claim(TokenService.ROLES_CLAIM, List.of(TokenService.ROLE_RESTAURANT_SETUP)))
                .authorities(new SimpleGrantedAuthority(TokenService.ROLE_RESTAURANT_SETUP));
    }

    public static JwtRequestPostProcessor superAdmin() {
        return jwt()
                .jwt(j -> j.subject("admin:1")
                        .audience(List.of(TokenService.AUDIENCE_ADMIN))
                        .claim(TokenService.CLAIM_VERSION, 1)
                        .claim("aid", 1L)
                        .claim("email", "owner@platform.test")
                        .claim(TokenService.ROLES_CLAIM, List.of(TokenService.ROLE_SUPER_ADMIN)))
                .authorities(new SimpleGrantedAuthority(TokenService.ROLE_SUPER_ADMIN));
    }
}
