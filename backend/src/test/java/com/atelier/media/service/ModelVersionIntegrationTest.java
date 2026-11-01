package com.atelier.media.service;

import com.atelier.media.dto.ModelConfirmDto;
import com.atelier.media.dto.Attach3dRequestDto;
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
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual disposable H2 publication/retention transactions; S3 never leaves the mock. */
@SpringBootTest(properties={
        "spring.config.import=optional:classpath:model-versions-no-secrets.properties",
        "spring.datasource.url=jdbc:h2:mem:model_versions_product;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.locations=classpath:db/migration/h2","atelier.scheduling.enabled=false",
        "aws.access-key-id=AKIAIOSFODNN7EXAMPLE","aws.secret-access-key=synthetic-test-secret-not-for-aws",
        "aws.region=us-east-1","aws.s3.bucket=atelier-test-bucket"
})
@ActiveProfiles("test")
class ModelVersionIntegrationTest {
    @Autowired DishService owner;
    @Autowired ModelRequestService adminPublication;
    @Autowired ModelHistoryService history;
    @Autowired DurableCleanup cleanup;
    @Autowired RestaurantRepository restaurants;
    @Autowired MediaRepository media;
    @Autowired ModelRequestRepository requests;
    @Autowired PlatformUserRepository admins;
    @Autowired TransactionTemplate tx;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @MockBean S3Storage storage;
    Restaurant restaurant;
    Media dish;
    PlatformUser admin;
    String oldKey,newKey;

    @BeforeEach void seed(){
        String slug="versions-"+UUID.randomUUID();
        restaurant=TestFixtures.restaurant(1,slug);restaurant.setId(null);restaurant=restaurants.saveAndFlush(restaurant);
        dish=TestFixtures.mediaWithModel(1,restaurant.getId(),slug);dish.setId(null);dish.setVersion(null);oldKey=dish.getModelS3Key();
        newKey="restaurant-atelier/"+slug+"/models/new.glb";
        dish.setPendingModelS3Key(newKey);dish.setPendingModelSizeBytes(500L);dish.setPendingModelContentType("model/gltf-binary");dish.setPendingModelCreatedAt(Instant.now());dish=media.saveAndFlush(dish);
        admin=admins.saveAndFlush(PlatformUser.builder().email(slug+"@platform.test").displayName("Model operator").passwordHash("synthetic-test-placeholder").mustChangePassword(false).build());
        when(storage.bucket()).thenReturn("atelier-test-bucket");
        when(storage.tenantPrefix(anyString())).thenAnswer(i->"restaurant-atelier/"+i.getArgument(0)+"/");
        when(storage.privateTenantPrefix(anyString())).thenAnswer(i->"restaurant-atelier-private/"+i.getArgument(0)+"/");
        when(storage.publicUrl(anyString())).thenAnswer(i->"https://example.test/"+i.getArgument(0));
        when(storage.head(anyString(),anyString())).thenReturn(HeadObjectResponse.builder().contentLength(500L).contentType("model/gltf-binary").eTag("synthetic-etag").build());
        when(storage.deleteKeys(anyString(),anyCollection())).thenAnswer(i->new S3DeletionResult(new HashSet<>((Collection<String>)i.getArgument(1)),Map.of(),Set.of(),true));
    }
    @Test void ownerReplacementRecordsTruthfulVersionAndRetainsUnknownLegacyProvenance(){
        confirmOwner();
        var versions=history.list(restaurant.getId(),dish.getId(),0);
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0)).containsEntry("origin","LEGACY_UNKNOWN").containsEntry("state","SUPERSEDED").containsEntry("retention","HELD");
        assertThat(versions.get(0).get("actor_id")).isNull();assertThat(versions.get(0).get("published_at")).isNull();
        assertThat(versions.get(1)).containsEntry("origin","RESTAURANT_UPLOAD").containsEntry("state","CURRENT").containsEntry("retention","HELD");
        assertThat(((Number)versions.get(1).get("actor_id")).longValue()).isEqualTo(restaurant.getId());
        assertThat(versions.get(1).get("published_at")).isNotNull();
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isEqualTo(newKey);
    }
    @Test void duplicateOwnerConfirmationDoesNotCreateAnotherVersion(){
        confirmOwner();confirmOwner();
        assertThat(history.list(restaurant.getId(),dish.getId(),0)).hasSize(2);
    }
    @Test void directAdminPublicationRecordsOriginWithoutInventingConciergeRequest(){
        approve();
        assertThat(requests.findByDishIdOrderByRequestedAtDesc(dish.getId())).as("Proactive admin upload is not a fabricated owner request").isEmpty();
        var versions=history.list(restaurant.getId(),dish.getId(),0);assertThat(versions).hasSize(2);var current=versions.stream().filter(v->"CURRENT".equals(v.get("state"))).findFirst().orElseThrow();
        assertThat(current).containsEntry("origin","ADMIN_DIRECT").containsEntry("actor_kind","ADMIN");
        assertThat(((Number)current.get("actor_id")).longValue()).isEqualTo(admin.getId());assertThat(current.get("request_id")).isNull();
    }
    @Test void conciergeFulfillmentRecordsRealRelatedRequest(){
        ModelRequest request=requests.saveAndFlush(ModelRequest.builder().restaurantId(restaurant.getId()).dishId(dish.getId()).status(ModelRequest.STATUS_PENDING).requestType(ModelRequest.TYPE_NEW).requestedAt(Instant.now()).build());
        approve();
        var current=history.list(restaurant.getId(),dish.getId(),0).stream().filter(v->"CURRENT".equals(v.get("state"))).findFirst().orElseThrow();
        assertThat(current).containsEntry("origin","CONCIERGE");assertThat(((Number)current.get("request_id")).longValue()).isEqualTo(request.getId());
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(ModelRequest.STATUS_COMPLETED);
    }
    @Test void modelDetachPreservesRetainedHistoryAndSupersedesCurrentVersion(){
        confirmOwner();owner.removeModel(tenant(),dish.getId());
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isNull();
        assertThat(history.list(restaurant.getId(),dish.getId(),0)).hasSize(2).allSatisfy(v->assertThat(v).containsEntry("retention","HELD").containsEntry("state","SUPERSEDED"));
    }
    @Test void retainedVersionAloneBlocksDurablePhysicalDeletion(){
        tx.executeWithoutResult(s->{history.published(media.findById(dish.getId()).orElseThrow(),newKey,"https://example.test/new.glb",500L,"ADMIN_DIRECT","ADMIN",admin.getId(),null);db.update("update media_details set pending_model_s3_key=null,pending_model_size_bytes=null,pending_model_content_type=null,pending_model_created_at=null where id=?",dish.getId());cleanup.record(restaurant.getId(),"atelier-test-bucket",newKey,"SUPERSEDED_VERSION");db.update("update s3_cleanup_tasks set available_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP) where object_key=?",newKey);});
        cleanup.runBatch();
        assertThat(db.queryForObject("select state from s3_cleanup_tasks where object_key=?",String.class,newKey)).isEqualTo("HELD");
        verify(storage,never()).deleteKeys(anyString(),argThat(keys->keys.contains(newKey)));
    }
    @Test void historyLookupCannotReadAnotherRestaurantsVersions(){
        confirmOwner();
        assertThat(history.list(restaurant.getId()+100000,dish.getId(),0)).isEmpty();
    }
    @Test void foreignOwnerCannotPublishOrCreateHistory(){
        Restaurant foreign=TestFixtures.restaurant(2,"foreign-"+UUID.randomUUID());foreign.setId(null);foreign=restaurants.saveAndFlush(foreign);Tenant wrong=new Tenant(foreign.getId(),foreign.getSlug());
        assertThatThrownBy(()->owner.confirmModelUpload(wrong,dish.getId(),new ModelConfirmDto(newKey,null))).isInstanceOf(RuntimeException.class);
        assertThat(history.list(restaurant.getId(),dish.getId(),0)).isEmpty();
    }
    @Test void suspensionDuringDetachedHeadPreventsVersionPublication(){
        when(storage.head(anyString(),anyString())).thenAnswer(i->{tx.executeWithoutResult(s->db.update("update restaurants set status='SUSPENDED' where id=?",restaurant.getId()));return HeadObjectResponse.builder().contentLength(500L).contentType("model/gltf-binary").build();});
        assertThatThrownBy(this::confirmOwner).isInstanceOf(RuntimeException.class);assertThat(history.list(restaurant.getId(),dish.getId(),0)).isEmpty();
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isEqualTo(oldKey);
    }
    @Test void concurrentConfirmationPublishesOnlyOneNewVersion() throws Exception {
        CountDownLatch bothHeads=new CountDownLatch(2),release=new CountDownLatch(1);
        when(storage.head(anyString(),anyString())).thenAnswer(i->{bothHeads.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return HeadObjectResponse.builder().contentLength(500L).contentType("model/gltf-binary").build();});
        ExecutorService executor=Executors.newFixedThreadPool(2);AtomicInteger successes=new AtomicInteger();
        try{List<Future<?>> work=new ArrayList<>();for(int i=0;i<2;i++)work.add(executor.submit(()->{try{confirmOwner();successes.incrementAndGet();}catch(RuntimeException ignored){}}));assertThat(bothHeads.await(10,TimeUnit.SECONDS)).isTrue();release.countDown();for(Future<?> f:work)f.get(15,TimeUnit.SECONDS);assertThat(successes.get()).isBetween(1,2);var versions=history.list(restaurant.getId(),dish.getId(),0);assertThat(versions).hasSize(2);assertThat(versions.stream().filter(v->"CURRENT".equals(v.get("state"))).count()).isEqualTo(1);}finally{release.countDown();executor.shutdownNow();}
    }
    @Test void ownerCannotConfirmAnAdminIssuedPendingModel(){
        tx.executeWithoutResult(s->history.issued(media.findById(dish.getId()).orElseThrow(),newKey,"ADMIN",admin.getId()));
        assertThatThrownBy(this::confirmOwner).isInstanceOf(RuntimeException.class);
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isEqualTo(oldKey);
        assertThat(history.list(restaurant.getId(),dish.getId(),0)).isEmpty();
    }
    @Test void administratorCannotClaimAnOwnerIssuedPendingModel(){
        tx.executeWithoutResult(s->history.issued(media.findById(dish.getId()).orElseThrow(),newKey,"RESTAURANT",restaurant.getId()));
        assertThatThrownBy(this::approve).isInstanceOf(RuntimeException.class);
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isEqualTo(oldKey);
        assertThat(history.list(restaurant.getId(),dish.getId(),0)).isEmpty();
    }
    @Test void differentAdministratorCannotClaimAnotherAdminsIssuedPendingModel(){
        tx.executeWithoutResult(s->history.issued(media.findById(dish.getId()).orElseThrow(),newKey,"ADMIN",admin.getId()));
        PlatformUser different=admins.saveAndFlush(PlatformUser.builder().email("other-"+UUID.randomUUID()+"@platform.test").displayName("Other model operator").passwordHash("synthetic-test-placeholder").mustChangePassword(false).build());
        assertThatThrownBy(()->adminPublication.approveModel(dish.getId(),new AdminModelApproveDto(newKey,"synthetic-etag","table"),new AdminPrincipal(different.getId(),different.getEmail()))).isInstanceOf(RuntimeException.class);
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isEqualTo(oldKey);
        assertThat(history.list(restaurant.getId(),dish.getId(),0)).isEmpty();
    }
    @Test void ownerVersionRetainsKnownIssuedActorAndTime(){
        tx.executeWithoutResult(s->history.issued(media.findById(dish.getId()).orElseThrow(),newKey,"RESTAURANT",restaurant.getId()));
        confirmOwner();
        var versions=history.list(restaurant.getId(),dish.getId(),0);assertThat(versions).hasSize(2);
        var current=versions.stream().filter(v->"CURRENT".equals(v.get("state"))).findFirst().orElseThrow();
        assertThat(current).containsEntry("issued_actor_kind","RESTAURANT");
        assertThat(((Number)current.get("issued_actor_id")).longValue()).isEqualTo(restaurant.getId());
        assertThat(current.get("issued_at")).isNotNull();
    }
    @Test void administratorVersionRetainsKnownIssuedActorAndTime(){
        tx.executeWithoutResult(s->history.issued(media.findById(dish.getId()).orElseThrow(),newKey,"ADMIN",admin.getId()));
        approve();
        var versions=history.list(restaurant.getId(),dish.getId(),0);assertThat(versions).hasSize(2);
        var current=versions.stream().filter(v->"CURRENT".equals(v.get("state"))).findFirst().orElseThrow();
        assertThat(current).containsEntry("issued_actor_kind","ADMIN").containsEntry("actor_kind","ADMIN");
        assertThat(((Number)current.get("issued_actor_id")).longValue()).isEqualTo(admin.getId());
        assertThat(current.get("issued_at")).isNotNull();
    }
    @Test void actualOwnerPresignAndConfirmationPreserveIssuedProvenance(){
        String issuedKey="restaurant-atelier/"+restaurant.getSlug()+"/model-3d/owner-issued.glb";mockSigning(issuedKey);
        var upload=owner.requestModelUpload(tenant(),dish.getId(),new Attach3dRequestDto("synthetic.glb","model/gltf-binary",500L,"table"));
        owner.confirmModelUpload(tenant(),dish.getId(),new ModelConfirmDto(upload.objectKey(),"synthetic-etag"));
        var current=history.list(restaurant.getId(),dish.getId(),0).stream().filter(v->"CURRENT".equals(v.get("state"))).findFirst().orElseThrow();
        assertThat(current).containsEntry("issued_actor_kind","RESTAURANT");assertThat(((Number)current.get("issued_actor_id")).longValue()).isEqualTo(restaurant.getId());assertThat(current.get("issued_at")).isNotNull();
    }
    @Test void actualAdminPresignAndConfirmationPreserveIssuedProvenance(){
        String issuedKey="restaurant-atelier/"+restaurant.getSlug()+"/models/admin-issued.glb";mockSigning(issuedKey);
        var actor=new AdminPrincipal(admin.getId(),admin.getEmail());
        var upload=adminPublication.presignModelUpload(dish.getId(),new Attach3dRequestDto("synthetic.glb","model/gltf-binary",500L,"table"),actor);
        adminPublication.approveModel(dish.getId(),new AdminModelApproveDto(upload.objectKey(),"synthetic-etag","table"),actor);
        var current=history.list(restaurant.getId(),dish.getId(),0).stream().filter(v->"CURRENT".equals(v.get("state"))).findFirst().orElseThrow();
        assertThat(current).containsEntry("issued_actor_kind","ADMIN");assertThat(((Number)current.get("issued_actor_id")).longValue()).isEqualTo(admin.getId());assertThat(current.get("issued_at")).isNotNull();
    }
    private void mockSigning(String key){
        when(storage.newKey(anyString(),anyString(),anyString(),anyString())).thenReturn(key);
        when(storage.presignPut(eq(key),anyString(),eq(500L))).thenReturn(new S3Storage.PresignedUpload("https://example.test/synthetic-upload",Instant.now().plusSeconds(300),300,Map.of("Content-Type","model/gltf-binary")));
    }
    private Tenant tenant(){return new Tenant(restaurant.getId(),restaurant.getSlug());}
    private void confirmOwner(){owner.confirmModelUpload(tenant(),dish.getId(),new ModelConfirmDto(newKey,"synthetic-etag"));}
    private void approve(){adminPublication.approveModel(dish.getId(),new AdminModelApproveDto(newKey,"synthetic-etag","table"),new AdminPrincipal(admin.getId(),admin.getEmail()));}
}
