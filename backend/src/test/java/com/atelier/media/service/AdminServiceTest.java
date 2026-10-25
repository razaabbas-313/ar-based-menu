package com.atelier.media.service;

import com.atelier.media.dto.AuthResponseDto;
import com.atelier.media.dto.admin.AdminRestaurantCreateDto;
import com.atelier.media.dto.admin.AdminRestaurantCreatedDto;
import com.atelier.media.dto.admin.AdminRestaurantDto;
import com.atelier.media.dto.admin.AdminRestaurantUpdateDto;
import com.atelier.media.dto.admin.RestaurantStatusUpdateDto;
import com.atelier.media.dto.admin.TemporaryPasswordDto;
import com.atelier.media.exception.ConflictException;
import com.atelier.media.model.AdminAuditLog;
import com.atelier.media.model.Media;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.repository.AdminAuditLogRepository;
import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.security.JwtKeys;
import com.atelier.media.security.TokenService;
import com.atelier.media.support.ServiceHarness;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.atelier.media.support.ServiceHarness.A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    private static final AdminPrincipal ADMIN = new AdminPrincipal(1L, "owner@platform.test");

    private final ServiceHarness h = new ServiceHarness();
    private final AdminAuditLogRepository audit = mock(AdminAuditLogRepository.class);
    private final StorageJanitor janitor = mock(StorageJanitor.class);
    private final SecretKey key = JwtKeys.secretKey(TestFixtures.JWT_SECRET);
    private final TokenService tokens = new TokenService(JwtKeys.encoder(key), h.props, Clock.systemUTC());
    private final AdminService service = new AdminService(h.restaurantRepository, h.mediaRepository, audit,
            new BCryptPasswordEncoder(4), tokens, h.storage, janitor, h.menuCache, h.props, h.tx);
    private Restaurant a;

    @BeforeEach
    void setUp() {
        a = TestFixtures.restaurant(A, "bistro-a");
        when(h.restaurantRepository.findById(A)).thenReturn(Optional.of(a));
        when(h.restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(h.restaurantRepository.saveAndFlush(any(Restaurant.class))).thenAnswer(inv -> {
            Restaurant r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });
    }

    @AfterEach
    void tearDown() {
        h.close();
    }

    @Test
    void createGeneratesATemporaryPasswordAndAudits() {
        AdminRestaurantCreatedDto created = service.create(
                new AdminRestaurantCreateDto("Chez Marie", null, "Marie", "Marie@Chez.fr", "+33 6 12 34 56 78", null), ADMIN);

        assertThat(created.restaurant().slug()).isEqualTo("chez-marie");
        assertThat(created.restaurant().email()).isEqualTo("marie@chez.fr");
        assertThat(created.restaurant().status()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(created.temporaryPassword()).startsWith("Temp-").hasSize(15);
        assertThat(created.restaurant().mustChangePassword()).isTrue();
        assertThat(created.restaurant().phoneNumber()).isEqualTo("+33 6 12 34 56 78");
        ArgumentCaptor<Restaurant> stored = ArgumentCaptor.forClass(Restaurant.class);
        verify(h.restaurantRepository).saveAndFlush(stored.capture());
        // Only the BCrypt hash is stored, and it matches the password shown once to the admin.
        assertThat(stored.getValue().getPasswordHash()).startsWith("$2").doesNotContain(created.temporaryPassword());
        assertThat(new BCryptPasswordEncoder().matches(created.temporaryPassword(), stored.getValue().getPasswordHash())).isTrue();
        assertThat(stored.getValue().getMustChangePassword()).isTrue();
        ArgumentCaptor<AdminAuditLog> entry = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(audit).save(entry.capture());
        assertThat(entry.getValue().getAction()).isEqualTo("CREATE");
        assertThat(entry.getValue().getAdminEmail()).isEqualTo("owner@platform.test");
    }

    @Test
    void temporaryPasswordsAreRandomAndReadable() {
        String one = service.temporaryPassword();
        String two = service.temporaryPassword();
        assertThat(one).matches("Temp-[A-HJ-NP-Za-km-z2-9]{10}");
        assertThat(one).isNotEqualTo(two);
    }

    @Test
    void createRejectsATakenSlugOrEmail() {
        when(h.restaurantRepository.existsBySlug("bistro-b")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new AdminRestaurantCreateDto("Bistro B", "bistro-b", "O", "new@x.com", null, null), ADMIN))
                .isInstanceOf(ConflictException.class);

        when(h.restaurantRepository.existsByEmail("taken@x.com")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new AdminRestaurantCreateDto("Fresh Place", null, "O", "Taken@X.com", null, null), ADMIN))
                .isInstanceOf(ConflictException.class);
        verify(h.restaurantRepository, never()).saveAndFlush(any());
    }

    @Test
    void resetOwnerPasswordForcesANewChangeAndAudits() {
        String before = a.getPasswordHash();

        TemporaryPasswordDto reset = service.resetOwnerPassword(A, ADMIN);

        assertThat(reset.temporaryPassword()).startsWith("Temp-");
        assertThat(a.getPasswordHash()).isNotEqualTo(before);
        assertThat(new BCryptPasswordEncoder().matches(reset.temporaryPassword(), a.getPasswordHash())).isTrue();
        assertThat(a.getMustChangePassword()).isTrue();
        assertThat(reset.restaurant().mustChangePassword()).isTrue();
        ArgumentCaptor<AdminAuditLog> entry = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(audit).save(entry.capture());
        assertThat(entry.getValue().getAction()).isEqualTo("RESET_PASSWORD");
    }

    @Test
    void slugAvailabilityNormalizesAndChecksReservedAndTaken() {
        assertThat(service.slugAvailability("Saffron House", null).slug()).isEqualTo("saffron-house");
        assertThat(service.slugAvailability("Saffron House", null).available()).isTrue();
        assertThat(service.slugAvailability("admin", null).available()).isFalse();
        when(h.restaurantRepository.existsBySlug("bistro-b")).thenReturn(true);
        assertThat(service.slugAvailability("bistro-b", null).available()).isFalse();
        when(h.restaurantRepository.existsBySlugAndIdNot("bistro-a", A)).thenReturn(false);
        assertThat(service.slugAvailability("bistro-a", A).available()).isTrue();
    }

    @Test
    void slugChangeEvictsBothOldAndNewMenus() {
        h.menu().put("bistro-nouveau", "stale");

        AdminRestaurantDto dto = service.update(A, new AdminRestaurantUpdateDto("Bistro Nouveau", "Bistro Nouveau", "O", "a@x.com"), ADMIN);

        assertThat(dto.slug()).isEqualTo("bistro-nouveau");
        assertThat(h.cached("bistro-a")).isFalse();
        assertThat(h.cached("bistro-nouveau")).isFalse();
        assertThat(h.cached("bistro-b")).isTrue();
    }

    @Test
    void slugTakenByAnotherRestaurantIsAConflict() {
        when(h.restaurantRepository.existsBySlugAndIdNot("bistro-b", A)).thenReturn(true);

        assertThatThrownBy(() -> service.update(A, new AdminRestaurantUpdateDto("X", "bistro-b", "O", "a@x.com"), ADMIN))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void suspendingEvictsTheMenu() {
        AdminRestaurantDto dto = service.updateStatus(A, new RestaurantStatusUpdateDto(RestaurantStatus.SUSPENDED, "unpaid"), ADMIN);

        assertThat(dto.status()).isEqualTo(RestaurantStatus.SUSPENDED);
        assertThat(h.cached("bistro-a")).isFalse();
    }

    @Test
    void softDeleteHidesButKeepsFiles() {
        AdminRestaurantDto dto = service.softDelete(A, ADMIN);

        assertThat(dto.deletedAt()).isNotNull();
        assertThat(dto.status()).isEqualTo(RestaurantStatus.SUSPENDED);
        verify(janitor, never()).wipeRestaurantAssets(anyString(), any(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void hardDeleteCommitsThenWipesEveryKnownFileAsynchronously() {
        Media photoAndModel = TestFixtures.mediaWithModel(5L, A, "bistro-a");
        Media pending = TestFixtures.media(6L, A, "bistro-a", com.atelier.media.model.UploadStatus.COMPLETED);
        pending.setPendingModelS3Key("restaurant-atelier/bistro-a/model-3d/pending.glb");
        a.setLogoS3Key("restaurant-atelier/bistro-a/branding/logo-1.webp");
        when(h.mediaRepository.findByRestaurantId(A)).thenReturn(List.of(photoAndModel, pending));

        AdminService.HardDeleteResult result = service.hardDelete(A, ADMIN);

        assertThat(result.objectsScheduled()).isEqualTo(5);
        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
        InOrder order = inOrder(h.restaurantRepository, h.txManager, janitor);
        order.verify(h.restaurantRepository).delete(a);
        order.verify(h.txManager).commit(any());
        order.verify(janitor).wipeRestaurantAssets(eq("atelier-test-bucket"), keys.capture(), eq("restaurant-atelier/bistro-a/"));
        assertThat(keys.getValue()).contains(photoAndModel.getS3Key(), photoAndModel.getModelS3Key(),
                "restaurant-atelier/bistro-a/model-3d/pending.glb", "restaurant-atelier/bistro-a/branding/logo-1.webp");
    }

    @Test
    void impersonationIssuesAShortLivedRestaurantSessionAndIsAudited() {
        AuthResponseDto auth = service.impersonate(A, ADMIN);

        Jwt jwt = JwtKeys.decoder(key, TestFixtures.ISSUER).decode(auth.token());
        assertThat(jwt.getClaimAsString(TokenService.IMPERSONATED_BY_CLAIM)).isEqualTo("owner@platform.test");
        assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly(TokenService.ROLE_RESTAURANT);
        assertThat(auth.impersonatedBy()).isEqualTo("owner@platform.test");
        assertThat(auth.expiresInSeconds()).isEqualTo(3600);
        verify(audit).save(any(AdminAuditLog.class));
    }

    @Test
    void cannotImpersonateASuspendedRestaurant() {
        a.setStatus(RestaurantStatus.SUSPENDED);

        assertThatThrownBy(() -> service.impersonate(A, ADMIN)).isInstanceOf(ConflictException.class);
    }

    @Test
    void update3dPermissionTogglesSettingAndAudits() {
        assertThat(a.getCanUpload3d()).isTrue();
        AdminRestaurantDto dto = service.update3dPermission(A, false, ADMIN);

        assertThat(dto.canUpload3d()).isFalse();
        assertThat(a.getCanUpload3d()).isFalse();
        verify(audit).save(any(AdminAuditLog.class));
    }
}
