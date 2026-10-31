package com.atelier.media.service;

import com.atelier.media.model.*;
import com.atelier.media.repository.*;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = {"spring.config.import=optional:classpath:no-secrets.properties",
        "spring.datasource.url=jdbc:h2:mem:durablecleanup;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.locations=classpath:db/migration/h2", "atelier.scheduling.enabled=false"})
@ActiveProfiles("test")
class DurableCleanupTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired RestaurantRepository restaurants;
    @Autowired MediaRepository media;
    @Autowired DurableCleanup cleanup;
    @Autowired ModelRequestService concierge;
    @MockBean S3Storage storage;
    Restaurant tenant;
    Media dish;

    @BeforeEach void setup() {
        tx.executeWithoutResult(s -> {jdbc.update("delete from restaurants");jdbc.update("delete from s3_cleanup_tasks");});
        when(storage.bucket()).thenReturn("atelier-test-bucket");
        when(storage.tenantPrefix(anyString())).thenAnswer(i -> "restaurant-atelier/" + i.getArgument(0) + "/");
        when(storage.privateTenantPrefix(anyString())).thenAnswer(i -> "restaurant-atelier-private/" + i.getArgument(0) + "/");
        String slug = "cleanup-" + UUID.randomUUID();
        tenant = TestFixtures.restaurant(1, slug); tenant.setId(null);
        tenant = restaurants.saveAndFlush(tenant);
        dish = TestFixtures.media(1, tenant.getId(), slug, UploadStatus.COMPLETED); dish.setId(null); dish.setVersion(null);
        dish = media.saveAndFlush(dish);
    }
    void release() { tx.executeWithoutResult(s -> { Media d = media.findById(dish.getId()).orElseThrow(); d.setS3Key(d.getS3Key()+"-new"); }); }
    int count(String state) { return jdbc.queryForObject("select count(*) from s3_cleanup_tasks where state=?", Integer.class, state); }
    void due() { tx.executeWithoutResult(s -> jdbc.update("update s3_cleanup_tasks set available_at=?, lease_until=?", java.sql.Timestamp.from(Instant.EPOCH), java.sql.Timestamp.from(Instant.EPOCH))); }

    @Test void intentCommitsWithReferenceAndRollsBackWithReference() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> { Media d=media.findById(dish.getId()).orElseThrow(); d.setS3Key("changed"); media.flush(); throw new IllegalStateException(); })).isInstanceOf(IllegalStateException.class);
        assertThat(count("READY")).isZero();
        release(); assertThat(count("READY")).isOne(); verifyNoInteractionsExceptConfiguration();
    }
    void verifyNoInteractionsExceptConfiguration() { verify(storage, never()).deleteKeys(anyString(), anyCollection()); }

    @Test void unknownSurvivesNewWorkerAndRetriesOutsideTransaction() {
        release(); due();
        when(storage.deleteKeys(anyString(), anyCollection())).thenAnswer(i -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new S3DeletionResult(Set.of(), Map.of(), Set.copyOf(i.getArgument(1)), true);
        });
        cleanup.runBatch(); assertThat(count("RETRY")).isOne();
        due();
        when(storage.deleteKeys(anyString(), anyCollection())).thenAnswer(i -> new S3DeletionResult(Set.copyOf(i.getArgument(1)), Map.of(), Set.of(), true));
        new DurableCleanup(jdbc, tx, storage, TestFixtures.atelierProperties()).runBatch(); assertThat(count("DONE")).isOne();
    }
    @Test void failedDeletionBacksOffAndBecomesManualWithoutLosingKey() {
        release(); due();
        when(storage.deleteKeys(anyString(), anyCollection())).thenAnswer(i -> new S3DeletionResult(Set.of(), Map.of(dish.getS3Key(),"AccessDenied"), Set.of(), true));
        for(int n=0;n<8;n++){due(); cleanup.runBatch();}
        assertThat(count("MANUAL")).isOne();
        assertThat(jdbc.queryForObject("select object_key from s3_cleanup_tasks",String.class)).isEqualTo(dish.getS3Key());
        assertThat(jdbc.queryForObject("select last_error_code from s3_cleanup_tasks",String.class)).isEqualTo("AccessDenied");
    }
    @Test void activeAndForeignObjectsAreHeld() {
        cleanup.record(tenant.getId(), "atelier-test-bucket", dish.getS3Key(), "test");
        cleanup.record(tenant.getId(), "atelier-test-bucket", "restaurant-atelier/foreign/image.webp", "test");
        due(); cleanup.runBatch(); assertThat(count("HELD")).isEqualTo(2);
        verifyNoInteractionsExceptConfiguration();
    }
    @Test void partialResultSettlesEachKeyIndependentlyAndDeduplicatesIntent() {
        release();
        String second="restaurant-atelier/"+tenant.getSlug()+"/unused.webp";
        cleanup.record(tenant.getId(),"atelier-test-bucket",second,"test");
        cleanup.record(tenant.getId(),"atelier-test-bucket",second,"duplicate");
        due();
        when(storage.deleteKeys(anyString(),anyCollection())).thenReturn(new S3DeletionResult(Set.of(dish.getS3Key()),Map.of(second,"AccessDenied"),Set.of(),true));
        cleanup.runBatch(); assertThat(count("DONE")).isOne(); assertThat(count("RETRY")).isOne();
        cleanup.runBatch(); verify(storage,times(1)).deleteKeys(anyString(),anyCollection());
    }
    @Test void crashAfterAcknowledgementLeavesRecoverableLease() {
        release(); due();
        when(storage.deleteKeys(anyString(),anyCollection())).thenThrow(new AssertionError("simulated death after remote acknowledgement before settlement"));
        assertThatThrownBy(cleanup::runBatch).isInstanceOf(AssertionError.class);
        assertThat(count("LEASED")).isOne();
        cleanup.runBatch(); verify(storage,times(1)).deleteKeys(anyString(),anyCollection());
        due(); doAnswer(i->new S3DeletionResult(Set.copyOf(i.getArgument(1)),Map.of(),Set.of(),true)).when(storage).deleteKeys(anyString(),anyCollection());
        new DurableCleanup(jdbc,tx,storage,TestFixtures.atelierProperties()).runBatch();
        assertThat(count("DONE")).isOne();
    }
    @Test void secondWorkerCannotDeleteAnUnexpiredLease() throws Exception {
        release(); due();
        var entered=new java.util.concurrent.CountDownLatch(1); var finish=new java.util.concurrent.CountDownLatch(1);
        when(storage.deleteKeys(anyString(),anyCollection())).thenAnswer(i->{entered.countDown();assertThat(finish.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return new S3DeletionResult(Set.copyOf(i.getArgument(1)),Map.of(),Set.of(),true);});
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {var first=executor.submit(cleanup::runBatch);assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            new DurableCleanup(jdbc,tx,storage,TestFixtures.atelierProperties()).runBatch(); finish.countDown();first.get(10,java.util.concurrent.TimeUnit.SECONDS);
            verify(storage,times(1)).deleteKeys(anyString(),anyCollection());assertThat(count("DONE")).isOne();
        } finally {finish.countDown();executor.shutdownNow();}
    }
    @Test void restaurantCascadePreservesKnownManifestAndRetainedHistory() {
        String model="restaurant-atelier/"+tenant.getSlug()+"/models/retained.glb";
        tx.executeWithoutResult(s -> jdbc.update("insert into model_requests(restaurant_id,dish_id,status,request_type,completed_model_key,requested_at) values(?,?,'COMPLETED','NEW',?,?)",tenant.getId(),dish.getId(),model,java.sql.Timestamp.from(Instant.now())));
        tx.executeWithoutResult(s->restaurants.delete(restaurants.findById(tenant.getId()).orElseThrow()));
        assertThat(media.findById(dish.getId())).isEmpty();
        due();when(storage.deleteKeys(anyString(),anyCollection())).thenAnswer(i->new S3DeletionResult(Set.of(),Map.of(),Set.copyOf(i.getArgument(1)),true));
        cleanup.runBatch();assertThat(count("RETRY")).isOne();assertThat(count("HELD")).isOne();
        assertThat(jdbc.queryForObject("select count(*) from s3_cleanup_tasks where object_key=?",Integer.class,model)).isOne();
    }
    @Test void gracePeriodPreventsEarlyDeletionButDoesNotProveLatePutSafety() {
        release();cleanup.runBatch();verifyNoInteractionsExceptConfiguration();assertThat(count("READY")).isOne();
    }
    @Test void retiredKeyCannotBePublishedAgain() {
        release();
        assertThatThrownBy(()->tx.executeWithoutResult(s->{Media current=media.findById(dish.getId()).orElseThrow();current.setS3Key(dish.getS3Key());media.flush();})).isInstanceOf(RuntimeException.class);
        assertThat(media.findById(dish.getId()).orElseThrow().getS3Key()).endsWith("-new");
    }
    @Test void brandingAndPendingReleasesAreCapturedByTheSameTransaction() {
        String logo="restaurant-atelier/"+tenant.getSlug()+"/branding/logo.webp";
        String pending="restaurant-atelier/"+tenant.getSlug()+"/model-3d/pending.glb";
        tx.executeWithoutResult(s->{Restaurant r=restaurants.findById(tenant.getId()).orElseThrow();r.setLogoS3Key(logo);
            Media m=media.findById(dish.getId()).orElseThrow();m.setPendingModelS3Key(pending);});
        tx.executeWithoutResult(s->{restaurants.findById(tenant.getId()).orElseThrow().setLogoS3Key(null);
            media.findById(dish.getId()).orElseThrow().setPendingModelS3Key(null);});
        assertThat(count("READY")).isEqualTo(2);verifyNoInteractionsExceptConfiguration();
    }
    @Test void expiredConciergeReferencesMoveToDurableManifestIncludingUnknownResults() {
        String key="restaurant-atelier-private/"+tenant.getSlug()+"/requests/"+dish.getId()+"/front.webp";
        String json="[{\"slot\":\"front\",\"s3Key\":\""+key+"\"}]";
        tx.executeWithoutResult(s->{Media m=media.findById(dish.getId()).orElseThrow();m.setModelRequestStatus("COMPLETED");m.setModelCompletedAt(Instant.now().minusSeconds(15*86400));m.setModelRequestPhotos(json);
            jdbc.update("insert into model_requests(restaurant_id,dish_id,status,request_type,reference_photos,requested_at,completed_at) values(?,?,'COMPLETED','NEW',?,?,?)",tenant.getId(),dish.getId(),json,java.sql.Timestamp.from(Instant.now().minusSeconds(16*86400)),java.sql.Timestamp.from(Instant.now().minusSeconds(15*86400)));});
        assertThat(concierge.cleanupReferencePhotos()).isOne();assertThat(count("READY")).isOne();
        assertThat(media.findById(dish.getId()).orElseThrow().getModelRequestPhotos()).isEqualTo("[]");
        due();when(storage.deleteKeys(anyString(),anyCollection())).thenReturn(new S3DeletionResult(Set.of(),Map.of(),Set.of(key),true));
        cleanup.runBatch();assertThat(count("RETRY")).isOne();
        assertThat(jdbc.queryForObject("select object_key from s3_cleanup_tasks",String.class)).isEqualTo(key);
    }
    @Test void workerProcessesAtMostOneHundredTrackedKeysPerBatch() {
        tx.executeWithoutResult(s->{for(int n=0;n<105;n++)cleanup.record(tenant.getId(),"atelier-test-bucket","restaurant-atelier/"+tenant.getSlug()+"/unused/"+n,"BOUNDED_TEST");});
        due();when(storage.deleteKeys(anyString(),anyCollection())).thenAnswer(i->{Collection<String> keys=i.getArgument(1);assertThat(keys).hasSize(100);return new S3DeletionResult(Set.copyOf(keys),Map.of(),Set.of(),true);});
        cleanup.runBatch();assertThat(count("DONE")).isEqualTo(100);assertThat(count("READY")).isEqualTo(5);
    }
    @Test void retryDelayStartsAfterExternalCallSettles() {
        release();due();var ended=new java.util.concurrent.atomic.AtomicReference<Instant>();
        when(storage.deleteKeys(anyString(),anyCollection())).thenAnswer(i->{Thread.sleep(50);ended.set(Instant.now());return new S3DeletionResult(Set.of(),Map.of(),Set.copyOf(i.getArgument(1)),true);});
        cleanup.runBatch();Instant available=jdbc.queryForObject("select available_at from s3_cleanup_tasks",(rs,n)->rs.getTimestamp(1).toInstant());
        assertThat(available).isAfterOrEqualTo(ended.get().truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusSeconds(120));
    }
    @Test void malformedFirstPageDoesNotStarveLaterEligibleReferences() {
        tx.executeWithoutResult(s->{
            for(int n=0;n<100;n++) {
                Media broken=TestFixtures.media(1000+n,tenant.getId(),tenant.getSlug(),UploadStatus.COMPLETED);broken.setId(null);broken.setVersion(null);
                broken.setModelRequestStatus("COMPLETED");broken.setModelCompletedAt(Instant.now().minusSeconds(15*86400));broken.setModelRequestPhotos("malformed");media.save(broken);
            }
            Media valid=TestFixtures.media(9999,tenant.getId(),tenant.getSlug(),UploadStatus.COMPLETED);valid.setId(null);valid.setVersion(null);
            valid.setModelRequestStatus("COMPLETED");valid.setModelCompletedAt(Instant.now().minusSeconds(15*86400));
            valid.setModelRequestPhotos("[{\"s3Key\":\"restaurant-atelier-private/"+tenant.getSlug()+"/requests/valid.webp\"}]");media.save(valid);
        });
        assertThat(concierge.cleanupReferencePhotos()).isZero();
        assertThat(concierge.cleanupReferencePhotos()).isOne();assertThat(count("READY")).isOne();
    }
}
