package com.atelier.media.service;

import com.atelier.media.dto.ModelRequestConfirmDto;
import com.atelier.media.dto.admin.AdminModelApproveDto;
import com.atelier.media.model.*;
import com.atelier.media.repository.*;
import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.security.Tenant;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Real H2 transaction boundaries; S3 is always mocked and no local secrets are imported. */
@SpringBootTest(properties = {
        "spring.config.import=optional:classpath:b4b5-no-secrets.properties",
        "spring.datasource.url=jdbc:h2:mem:modelrequest_safety;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.locations=classpath:db/migration/h2",
        "atelier.scheduling.enabled=false",
        "aws.access-key-id=AKIAIOSFODNN7EXAMPLE",
        "aws.secret-access-key=synthetic-test-secret-not-for-aws",
        "aws.region=us-east-1", "aws.s3.bucket=atelier-test-bucket"
})
@ActiveProfiles("test")
class ModelRequestTransactionSafetyTest {
    @Autowired ModelRequestService service;
    @Autowired ModelHistoryService modelHistory;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @Autowired RestaurantRepository restaurants;
    @Autowired MediaRepository media;
    @Autowired ModelRequestRepository requests;
    @Autowired ModelRequestPendingKeyRepository pending;
    @Autowired PlatformUserRepository admins;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean S3Storage storage;
    private Restaurant restaurant;
    private Media dish;
    private PlatformUser admin;
    private String referenceKey;
    private String modelKey;

    @BeforeEach
    void seedDisposableData() {
        String slug = "safety-" + UUID.randomUUID();
        restaurant = TestFixtures.restaurant(1L, slug);
        restaurant.setId(null);
        restaurant = restaurants.saveAndFlush(restaurant);
        dish = TestFixtures.media(1L, restaurant.getId(), slug, UploadStatus.COMPLETED);
        dish.setId(null);
        dish = media.saveAndFlush(dish);
        admin = admins.saveAndFlush(PlatformUser.builder().email(slug + "@platform.test")
                .displayName("Safety operator").passwordHash("test-only-placeholder")
                .mustChangePassword(false).build());
        referenceKey = "restaurant-atelier-private/" + slug + "/requests/" + dish.getId() + "/ref-front.webp";
        modelKey = "restaurant-atelier/" + slug + "/models/twin.glb";
        dish.setPendingModelS3Key(modelKey);
        dish.setPendingModelSizeBytes(500L);
        dish.setPendingModelContentType("model/gltf-binary");
        dish.setPendingModelCreatedAt(Instant.now());
        dish = media.saveAndFlush(dish);
        pending.saveAndFlush(ModelRequestPendingKey.builder().restaurantId(restaurant.getId())
                .dishId(dish.getId()).s3Key(referenceKey).slot("front").createdAt(Instant.now()).build());
        when(storage.bucket()).thenReturn("atelier-test-bucket");
        when(storage.privateTenantPrefix(slug)).thenReturn("restaurant-atelier-private/" + slug + "/");
        when(storage.tenantPrefix(slug)).thenReturn("restaurant-atelier/" + slug + "/");
        when(storage.publicUrl(anyString())).thenAnswer(invocation -> "https://example.test/media");
    }

    @Test
    void confirmationHeadRunsOutsideDatabaseTransaction() {
        when(storage.head(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("S3 HEAD must not hold a database transaction or pessimistic row lock").isFalse();
            return imageHead();
        });
        confirm();
        assertThat(media.findById(dish.getId()).orElseThrow().getModelRequestStatus()).isEqualTo("PENDING");
    }

    @Test
    void approvalHeadRunsOutsideDatabaseTransaction() {
        when(storage.head(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("S3 HEAD must precede the short publication transaction").isFalse();
            return modelHead();
        });
        approve();
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isEqualTo(modelKey);
    }

    @Test
    void suspensionDuringReferenceHeadPreventsRequestPublication() {
        when(storage.head(anyString(), anyString())).thenAnswer(invocation -> {
            independently(() -> {
                Restaurant changed = restaurants.findById(restaurant.getId()).orElseThrow();
                changed.setStatus(RestaurantStatus.SUSPENDED);
                restaurants.saveAndFlush(changed);
            });
            return imageHead();
        });
        assertThatThrownBy(this::confirm).isInstanceOf(RuntimeException.class);
        assertThat(media.findById(dish.getId()).orElseThrow().getModelRequestStatus()).isEqualTo("NONE");
        assertThat(requests.findByDishIdOrderByRequestedAtDesc(dish.getId())).isEmpty();
        assertThat(pending.findByRestaurantIdAndDishId(restaurant.getId(), dish.getId())).hasSize(1);
    }

    @Test
    void adminRevocationDuringModelHeadPreservesLiveAssetAndHistory() {
        when(storage.head(anyString(), anyString())).thenAnswer(invocation -> {
            independently(() -> {
                PlatformUser changed = admins.findById(admin.getId()).orElseThrow();
                changed.incrementTokenVersion();
                admins.saveAndFlush(changed);
            });
            return modelHead();
        });
        assertThatThrownBy(this::approve).isInstanceOf(RuntimeException.class);
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isNull();
        assertThat(requests.findByDishIdOrderByRequestedAtDesc(dish.getId())).isEmpty();
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getModel3dCount()).isZero();
    }

    @Test
    void tokenRevocationDuringReferenceHeadPreservesIssuedReferences() {
        when(storage.head(anyString(), anyString())).thenAnswer(invocation -> {
            independently(() -> {
                Restaurant changed = restaurants.findById(restaurant.getId()).orElseThrow();
                changed.incrementTokenVersion();
                restaurants.saveAndFlush(changed);
            });
            return imageHead();
        });
        assertThatThrownBy(this::confirm).isInstanceOf(RuntimeException.class);
        assertThat(media.findById(dish.getId()).orElseThrow().getModelRequestStatus()).isEqualTo("NONE");
        assertThat(requests.findByDishIdOrderByRequestedAtDesc(dish.getId())).isEmpty();
        assertThat(pending.findByRestaurantIdAndDishId(restaurant.getId(), dish.getId())).hasSize(1);
    }

    @Test
    void simultaneousConfirmationsCreateOneRequestHistory() throws Exception {
        CyclicBarrier headBarrier = new CyclicBarrier(2);
        when(storage.head(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            headBarrier.await(10, TimeUnit.SECONDS);
            return imageHead();
        });
        assertThat(runConcurrently(this::confirm)).containsExactlyInAnyOrder(true, false);
        assertThat(requests.findByDishIdOrderByRequestedAtDesc(dish.getId())).hasSize(1);
        assertThat(media.findById(dish.getId()).orElseThrow().getModelRequestStatus()).isEqualTo("PENDING");
        assertThat(pending.findByRestaurantIdAndDishId(restaurant.getId(), dish.getId())).isEmpty();
    }

    @Test
    void repeatedConfirmationCannotCreateAnotherHistory() {
        when(storage.head(anyString(), anyString())).thenReturn(imageHead());
        confirm();
        assertThatThrownBy(this::confirm).isInstanceOf(RuntimeException.class);
        assertThat(requests.findByDishIdOrderByRequestedAtDesc(dish.getId())).hasSize(1);
    }

    @Test
    void simultaneousApprovalsPublishAndAccountOnlyOnce() throws Exception {
        CyclicBarrier headBarrier = new CyclicBarrier(2);
        when(storage.head(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            headBarrier.await(10, TimeUnit.SECONDS);
            return modelHead();
        });
        assertThat(runConcurrently(this::approve)).containsExactlyInAnyOrder(true, false);
        assertThat(requests.findByDishIdOrderByRequestedAtDesc(dish.getId())).isEmpty();
        assertThat(modelHistory.list(restaurant.getId(),dish.getId(),0)).hasSize(1).allSatisfy(v->assertThat(v).containsEntry("origin","ADMIN_DIRECT").containsEntry("state","CURRENT").containsEntry("retention","HELD"));
        Media published = media.findById(dish.getId()).orElseThrow();
        assertThat(published.getModelS3Key()).isEqualTo(modelKey);
        assertThat(published.getPendingModelS3Key()).isNull();
        Restaurant accounted = restaurants.findById(restaurant.getId()).orElseThrow();
        assertThat(accounted.getModel3dCount()).isEqualTo(1);
        assertThat(accounted.getStorageUsedBytes()).isEqualTo(500L);
    }

    @Test
    void failedHeadLeavesPreviousLiveModelAndAccountingUntouched() {
        String oldKey = "restaurant-atelier/" + restaurant.getSlug() + "/models/old.glb";
        dish.setModelS3Key(oldKey);
        dish.setModelS3Url("https://example.test/old");
        dish.setModelFileSizeBytes(250L);
        dish.setHasArSupport(true);
        media.saveAndFlush(dish);
        restaurant.setModel3dCount(1);
        restaurant.setStorageUsedBytes(250L);
        restaurants.saveAndFlush(restaurant);
        when(storage.head(anyString(), anyString())).thenThrow(
                software.amazon.awssdk.core.exception.SdkClientException.create("synthetic HEAD timeout"));
        assertThatThrownBy(this::approve).isInstanceOf(RuntimeException.class);
        Media unchanged = media.findById(dish.getId()).orElseThrow();
        assertThat(unchanged.getModelS3Key()).isEqualTo(oldKey);
        assertThat(unchanged.getPendingModelS3Key()).isEqualTo(modelKey);
        assertThat(requests.findByDishIdOrderByRequestedAtDesc(dish.getId())).isEmpty();
        Restaurant accounted = restaurants.findById(restaurant.getId()).orElseThrow();
        assertThat(accounted.getModel3dCount()).isEqualTo(1);
        assertThat(accounted.getStorageUsedBytes()).isEqualTo(250L);
        verify(storage, never()).deleteBestEffort(anyString());
    }

    private List<Boolean> runConcurrently(Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                try {
                    action.run();
                    return true;
                } catch (RuntimeException expectedConflict) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void successiveDirectApprovalsSupersedeVersionsWithoutFakeRequestsOrDoubleAccounting() {
        when(storage.head(anyString(),anyString())).thenReturn(modelHead());
        String firstKey=modelKey;
        approve();
        modelKey=modelKey+"-v2.glb";
        independently(()->{Media current=media.findById(dish.getId()).orElseThrow();current.setPendingModelS3Key(modelKey);current.setPendingModelSizeBytes(500L);media.saveAndFlush(current);});
        approve();
        assertThat(requests.findByDishIdOrderByRequestedAtDesc(dish.getId())).isEmpty();
        var history=modelHistory.list(restaurant.getId(),dish.getId(),0);
        assertThat(history).hasSize(2).allSatisfy(v->assertThat(v).containsEntry("origin","ADMIN_DIRECT").containsEntry("retention","HELD"));
        assertThat(db.queryForObject("select state from model_versions where dish_id=? and object_key=?",String.class,dish.getId(),firstKey)).isEqualTo("SUPERSEDED");
        assertThat(db.queryForObject("select state from model_versions where dish_id=? and object_key=?",String.class,dish.getId(),modelKey)).isEqualTo("CURRENT");
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(500L);
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getModel3dCount()).isOne();
    }

    private void confirm() {
        service.confirmRequest(new Tenant(restaurant.getId(), restaurant.getSlug(), false, restaurant.getTokenVersion()), dish.getId(), new ModelRequestConfirmDto(
                List.of(new ModelRequestConfirmDto.UploadedPhotoRef("front", referenceKey, null)), null));
    }

    private void approve() {
        service.approveModel(dish.getId(), new AdminModelApproveDto(modelKey, null, null),
                new AdminPrincipal(admin.getId(), admin.getEmail(), admin.getTokenVersion()));
    }

    private void independently(Runnable action) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> action.run());
    }

    private HeadObjectResponse imageHead() {
        return HeadObjectResponse.builder().contentType("image/webp").contentLength(100L).build();
    }

    private HeadObjectResponse modelHead() {
        return HeadObjectResponse.builder().contentType("model/gltf-binary").contentLength(500L).build();
    }
}
