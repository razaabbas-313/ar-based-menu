package com.atelier.media.service;

import com.atelier.media.dto.PresignRequestDto;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real isolated transaction/locking tests; no AWS clients are invoked. */
@SpringBootTest(properties = {
        "spring.config.import=optional:classpath:entitlements-no-secrets.properties",
        "spring.datasource.url=jdbc:h2:mem:entitlement_foundation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.locations=classpath:db/migration/h2", "atelier.scheduling.enabled=false",
        "aws.access-key-id=AKIAIOSFODNN7EXAMPLE", "aws.secret-access-key=synthetic-test-secret-not-for-aws",
        "aws.region=us-east-1", "aws.s3.bucket=atelier-test-bucket"
})
@ActiveProfiles("test")
class EntitlementIntegrationTest {
    @Autowired EntitlementService service;
    @Autowired RestaurantRepository restaurants;
    @Autowired MediaRepository media;
    @Autowired PlatformUserRepository admins;
    @Autowired MediaService mediaService;
    @Autowired AdminService adminService;
    @Autowired BrandingService brandingService;
    @Autowired PublicMenuService menus;
    @Autowired ModelRequestService modelRequests;
    @Autowired ModelRequestPendingKeyRepository pendingKeys;
    @SpyBean(proxyTargetAware=true) EntitlementRepository entitlementDb;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @MockBean S3Storage storage;
    private Restaurant restaurant;
    private PlatformUser admin;

    @BeforeEach void seed() {
        String slug="entitlement-"+UUID.randomUUID();
        restaurant=TestFixtures.restaurant(1L,slug);restaurant.setId(null);restaurant.setCanUpload3d(false);
        restaurant=restaurants.saveAndFlush(restaurant);
        admin=admins.saveAndFlush(PlatformUser.builder().email(slug+"@platform.test").displayName("Plan operator")
                .passwordHash("test-only-placeholder").mustChangePassword(false).build());
        when(storage.bucket()).thenReturn("atelier-test-bucket");
        when(storage.tenantPrefix(anyString())).thenAnswer(i->"restaurant-atelier/"+i.getArgument(0)+"/");
        when(storage.privateTenantPrefix(anyString())).thenAnswer(i->"restaurant-atelier-private/"+i.getArgument(0)+"/");
        when(storage.newKey(anyString(),anyString(),anyString(),anyString())).thenAnswer(i->"restaurant-atelier/"+i.getArgument(0)+"/"+i.getArgument(1)+"/"+UUID.randomUUID()+"."+i.getArgument(3));
        when(storage.publicUrl(anyString())).thenReturn("https://example.test/media");
        when(storage.presignPut(anyString(),anyString(),anyLong())).thenReturn(new S3Storage.PresignedUpload("https://example.test/upload",Instant.now().plusSeconds(300),300,Map.of()));
    }

    @Test void legacyMappingPreservesExistingModelRestrictionAndBrowsing() {
        Map<String,Object> values=values(restaurant.getId());
        assertThat(values.get("model.ownerUpload")).isEqualTo(false);
        assertThat(values.get("model.concierge")).isEqualTo(true);
        assertThat((Collection<Object>)values.get("menu.allowedModes")).contains("SIMPLE","THREE_D","AR");
        assertThat(values.get("quota.dishes")).isEqualTo("UNLIMITED");
    }
    @Test void legacyDirectUploadGrantRemainsAvailableWithoutPremiumAssignment() {
        tx.executeWithoutResult(s->restaurants.findById(restaurant.getId()).orElseThrow().setCanUpload3d(true));
        assertThat(values(restaurant.getId()).get("model.ownerUpload")).isEqualTo(true);
    }
    @Test void createPlanStoresValidatedConfigurationAndRevision() {
        Map<String,Object> plan=plan(Map.of("quota.dishes",12));
        assertThat(number(plan,"id")).isPositive();assertThat(number(plan,"latestRevisionId")).isPositive();
        assertThat(service.plans()).anySatisfy(p->assertThat(number(p,"id")).isEqualTo(number(plan,"id")));
    }
    @Test void editedPlanCreatesRevisionAndDoesNotChangePinnedRestaurant() {
        Map<String,Object> first=plan(Map.of("quota.dishes",1));assign(first);
        Map<String,Object> edited=service.revisePlan(number(first,"id"),new EntitlementService.PlanWrite("Revised", "Changed quota",true,number(first,"version"),Map.of("quota.dishes",5)),principal());
        assertThat(number(edited,"latestRevisionId")).isNotEqualTo(number(first,"latestRevisionId"));
        assertThat(((Number)values(restaurant.getId()).get("quota.dishes")).longValue()).isOne();
        service.assign(restaurant.getId(),new EntitlementService.AssignmentWrite(number(edited,"latestRevisionId"),version(),"Explicit revision migration"),principal());
        assertThat(((Number)values(restaurant.getId()).get("quota.dishes")).longValue()).isEqualTo(5);
    }
    @Test void stalePlanVersionDoesNotOverwriteAnotherEditorsRevision() {
        Map<String,Object> first=plan(Map.of("quota.dishes",1));
        service.revisePlan(number(first,"id"),new EntitlementService.PlanWrite("Updated", "",true,number(first,"version"),Map.of("quota.dishes",2)),principal());
        assertThatThrownBy(()->service.revisePlan(number(first,"id"),new EntitlementService.PlanWrite("Stale", "",true,number(first,"version"),Map.of("quota.dishes",99)),principal())).isInstanceOf(RuntimeException.class);
    }
    @Test void deactivatedPlanCannotBeNewlyAssignedButExistingAssignmentSurvives() {
        Map<String,Object> first=plan(Map.of("quota.dishes",2));assign(first);
        Map<String,Object> inactive=service.revisePlan(number(first,"id"),new EntitlementService.PlanWrite("Inactive", "Retired template",false,number(first,"version"),Map.of("quota.dishes",2)),principal());
        assertThat(((Number)values(restaurant.getId()).get("quota.dishes")).longValue()).isEqualTo(2);
        assertThatThrownBy(()->service.assign(restaurant.getId(),new EntitlementService.AssignmentWrite(number(inactive,"latestRevisionId"),version(),"Cannot assign inactive"),principal())).isInstanceOf(RuntimeException.class);
    }
    @Test void explicitFalseAndZeroOverridesAreValuesRatherThanAbsence() {
        assign(plan(Map.of("quota.dishes",10,"model.concierge",true)));
        service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(0,version(),"Pause new dishes",null),principal());
        service.override(restaurant.getId(),"model.concierge",new EntitlementService.OverrideWrite(false,version(),"Pause concierge",null),principal());
        assertThat(((Number)values(restaurant.getId()).get("quota.dishes")).longValue()).isZero();
        assertThat(values(restaurant.getId()).get("model.concierge")).isEqualTo(false);
    }
    @Test void removedOverrideReturnsToAssignedRevision() {
        assign(plan(Map.of("quota.dishes",10)));
        service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(4,version(),"Temporary capacity",null),principal());
        service.removeOverride(restaurant.getId(),"quota.dishes",version(),"Return to inherited limit",principal());
        assertThat(((Number)values(restaurant.getId()).get("quota.dishes")).longValue()).isEqualTo(10);
    }
    @Test void expiredOverrideStopsAffectingPolicyWithoutScheduler() {
        assign(plan(Map.of("quota.dishes",10)));
        service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(99,version(),"Temporary grant",Instant.now().plusSeconds(3600)),principal());
        assertThat(((Number)values(restaurant.getId()).get("quota.dishes")).longValue()).isEqualTo(99);
        tx.executeWithoutResult(status->jdbc.update("update restaurant_entitlement_overrides set starts_at=?,expires_at=? where restaurant_id=? and feature_key=?",
                java.sql.Timestamp.from(Instant.EPOCH),java.sql.Timestamp.from(Instant.now().minusSeconds(60)),restaurant.getId(),"quota.dishes"));
        assertThat(((Number)values(restaurant.getId()).get("quota.dishes")).longValue()).isEqualTo(10);
    }
    @Test void staleRestaurantConfigurationVersionRejectsLostUpdate() {
        long before=version();
        service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(4,before,"First edit",null),principal());
        assertThatThrownBy(()->service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(8,before,"Stale edit",null),principal())).isInstanceOf(RuntimeException.class);
        assertThat(((Number)values(restaurant.getId()).get("quota.dishes")).longValue()).isEqualTo(4);
    }
    @Test void unknownFeatureKeyIsRejected() {
        assertThatThrownBy(()->plan(Map.of("invented.superpowers",true))).isInstanceOf(RuntimeException.class);
    }
    @Test void stringCannotMasqueradeAsBooleanOrNumericQuota() {
        assertThatThrownBy(()->plan(Map.of("model.ownerUpload","true"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->plan(Map.of("quota.dishes","10"))).isInstanceOf(RuntimeException.class);
    }
    @Test void negativeAndFractionalQuotasAreRejected() {
        assertThatThrownBy(()->plan(Map.of("quota.dishes",-1))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->plan(Map.of("quota.models",1.5))).isInstanceOf(RuntimeException.class);
    }
    @Test void conflictingMenuDependenciesAreRejected() {
        assertThatThrownBy(()->plan(Map.of("menu.allowedModes",List.of("AR")))).isInstanceOf(RuntimeException.class);
    }
    @Test void rawModelDependencyRejectsInvalidOverrideInsteadOfSilentlyMaskingGrant() {
        assign(plan(Map.of("menu.allowedModes",List.of("SIMPLE"),"model.ownerUpload",false,"model.concierge",false)));
        long before=version();
        assertThatThrownBy(()->service.override(restaurant.getId(),"model.ownerUpload",new EntitlementService.OverrideWrite(true,before,"Invalid simple-only upload grant",null),principal())).isInstanceOf(RuntimeException.class);
        assertThat(version()).isEqualTo(before);assertThat(values(restaurant.getId()).get("model.ownerUpload")).isEqualTo(false);
    }
    @Test void rawTableQrDependencyRejectsInvalidOverrideInsteadOfSilentlyMaskingGrant() {
        assign(plan(Map.of("qr.venue",false,"qr.table",false)));
        long before=version();
        assertThatThrownBy(()->service.override(restaurant.getId(),"qr.table",new EntitlementService.OverrideWrite(true,before,"Invalid table QR grant",null),principal())).isInstanceOf(RuntimeException.class);
        assertThat(version()).isEqualTo(before);assertThat(values(restaurant.getId()).get("qr.table")).isEqualTo(false);
    }
    @Test void overrideReasonIsMandatory() {
        assertThatThrownBy(()->service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(3,version()," ",null),principal())).isInstanceOf(RuntimeException.class);
    }
    @Test void revokedAdminCannotCreatePlansOrChangeRestaurantPolicy() {
        AdminPrincipal stale=principal();tx.executeWithoutResult(s->admins.findById(admin.getId()).orElseThrow().incrementTokenVersion());
        assertThatThrownBy(()->service.createPlan(new EntitlementService.PlanWrite("Blocked", "",true,0,Map.of()),stale)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(3,version(),"Blocked revoked actor",null),stale)).isInstanceOf(RuntimeException.class);
    }
    @Test void unusableAdminAccountCannotManageBusinessEntitlements() {
        tx.executeWithoutResult(s->admins.findById(admin.getId()).orElseThrow().setPasswordHash(PlatformUser.UNUSABLE_PASSWORD));
        assertThatThrownBy(()->plan(Map.of("quota.dishes",3))).isInstanceOf(RuntimeException.class);
    }
    @Test void restaurantOverrideDoesNotLeakAcrossTenants() {
        Restaurant other=TestFixtures.restaurant(2,"other-"+UUID.randomUUID());other.setId(null);other=restaurants.saveAndFlush(other);
        Object before=values(other.getId()).get("quota.dishes");
        service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(3,version(),"Tenant-specific",null),principal());
        assertThat(values(other.getId()).get("quota.dishes")).isEqualTo(before);
    }
    @Test void planDowngradePreservesExistingDishesAndBlocksGrowth() {
        Media existing=TestFixtures.media(1,restaurant.getId(),restaurant.getSlug(),UploadStatus.COMPLETED);existing.setId(null);existing.setVersion(null);existing=media.saveAndFlush(existing);
        long existingId=existing.getId();
        assign(plan(Map.of("quota.dishes",0)));
        assertThat(media.findById(existingId)).isPresent();
        assertThatThrownBy(()->mediaService.generatePresignedUploadUrl(tenant(),photo())).isInstanceOf(RuntimeException.class);
        assertThat(media.countByRestaurantId(restaurant.getId())).isOne();
    }
    @Test void twoConcurrentDishCreationsCannotConsumeTheSameFinalSlot() throws Exception {
        assign(plan(Map.of("quota.dishes",1)));
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(int n=0;n<2;n++)results.add(executor.submit(()->{start.await();try{mediaService.generatePresignedUploadUrl(tenant(),photo());return true;}catch(RuntimeException denied){return false;}}));
            start.countDown();int succeeded=0;for(var result:results)if(result.get(15,TimeUnit.SECONDS))succeeded++;
            assertThat(succeeded).isOne();assertThat(media.countByRestaurantId(restaurant.getId())).isOne();
        } finally {start.countDown();executor.shutdownNow();}
    }
    @Test void zeroConciergeQuotaAndDisabledOwnerUploadAreEnforced() {
        assign(plan(Map.of("quota.conciergePerPeriod",0,"model.ownerUpload",false)));
        assertThatThrownBy(()->tx.executeWithoutResult(s->{restaurants.findByIdForUpdate(restaurant.getId()).orElseThrow();service.requireConciergeCapacity(restaurant.getId());})).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{restaurants.findByIdForUpdate(restaurant.getId()).orElseThrow();service.requireModelCapacity(restaurant.getId(),999L,100L,true);})).isInstanceOf(RuntimeException.class);
    }
    @Test void failedUploadDoesNotConsumeANewDishReservationForever() {
        Media failed=TestFixtures.media(100,restaurant.getId(),restaurant.getSlug(),UploadStatus.FAILED);failed.setId(null);failed.setVersion(null);media.saveAndFlush(failed);
        assign(plan(Map.of("quota.dishes",1)));
        assertThatCode(()->mediaService.generatePresignedUploadUrl(tenant(),photo())).doesNotThrowAnyException();
        assertThat(media.countByRestaurantIdAndUploadStatus(restaurant.getId(),UploadStatus.PRESIGNED)).isOne();
    }
    @Test void confirmationRechecksDowngradedQuotaAfterHeadAndPreservesPendingRow() {
        assign(plan(Map.of("quota.dishes",1)));
        var issued=mediaService.generatePresignedUploadUrl(tenant(),photo());
        assign(plan(Map.of("quota.dishes",0)));
        when(storage.head(anyString(),eq(issued.objectKey()))).thenReturn(software.amazon.awssdk.services.s3.model.HeadObjectResponse.builder().contentLength(100L).contentType("image/webp").build());
        assertThatThrownBy(()->mediaService.confirmUpload(tenant(),new com.atelier.media.dto.UploadConfirmDto(issued.objectKey(),null,100L,null,null,false))).isInstanceOf(RuntimeException.class);
        assertThat(media.countByRestaurantIdAndUploadStatus(restaurant.getId(),UploadStatus.COMPLETED)).isZero();
        assertThat(media.countByRestaurantIdAndUploadStatus(restaurant.getId(),UploadStatus.PRESIGNED)).isOne();
    }
    @Test void existingModelReplacementDoesNotConsumeAnotherActiveModelSlot() {
        assign(plan(Map.of("menu.allowedModes",List.of("SIMPLE","THREE_D","AR"),"model.ownerUpload",true,"quota.models",1)));
        Media existing=TestFixtures.mediaWithModel(100,restaurant.getId(),restaurant.getSlug());existing.setId(null);existing.setVersion(null);existing=media.saveAndFlush(existing);
        long existingId=existing.getId();
        assertThatCode(()->tx.executeWithoutResult(s->{restaurants.findByIdForUpdate(restaurant.getId()).orElseThrow();service.requireModelCapacity(restaurant.getId(),existingId,100L,true);})).doesNotThrowAnyException();
        Media another=TestFixtures.media(101,restaurant.getId(),restaurant.getSlug(),UploadStatus.COMPLETED);another.setId(null);another.setVersion(null);another=media.saveAndFlush(another);
        long anotherId=another.getId();
        assertThatThrownBy(()->tx.executeWithoutResult(s->{restaurants.findByIdForUpdate(restaurant.getId()).orElseThrow();service.requireModelCapacity(restaurant.getId(),anotherId,100L,true);})).isInstanceOf(RuntimeException.class);
    }
    @Test void conciergeQuotaCountsOnlyUtcCalendarMonthSubmissions() {
        assign(plan(Map.of("model.concierge",true,"quota.conciergePerPeriod",1)));
        String operation=UUID.randomUUID().toString();
        tx.executeWithoutResult(s->jdbc.update("insert into entitlement_request_usage(operation_id,restaurant_id,dish_id,submitted_at) values(?,?,?,?)",operation,restaurant.getId(),123L,java.sql.Timestamp.from(Instant.now())));
        assertThatThrownBy(()->tx.executeWithoutResult(s->{restaurants.findByIdForUpdate(restaurant.getId()).orElseThrow();service.requireConciergeCapacity(restaurant.getId());})).isInstanceOf(RuntimeException.class);
        Instant oldMonth=java.time.YearMonth.now(java.time.ZoneOffset.UTC).minusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        tx.executeWithoutResult(s->jdbc.update("update entitlement_request_usage set submitted_at=? where operation_id=?",java.sql.Timestamp.from(oldMonth),operation));
        assertThatCode(()->tx.executeWithoutResult(s->{restaurants.findByIdForUpdate(restaurant.getId()).orElseThrow();service.requireConciergeCapacity(restaurant.getId());})).doesNotThrowAnyException();
    }
    @Test void entitlementMutationAndActorReasonAreRecordedAtomically() throws Exception {
        long before=jdbc.queryForObject("select count(*) from admin_audit_log where restaurant_id=?",Long.class,restaurant.getId());
        service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(3,version(),"Capacity exception for audit test",null),principal());
        assertThat(jdbc.queryForObject("select count(*) from admin_audit_log where restaurant_id=?",Long.class,restaurant.getId())).isGreaterThan(before);
        String evidence=String.join("\n",jdbc.query("select actor_email || ' ' || feature_key || ' ' || reason from entitlement_audit where restaurant_id=?",(rs,n)->rs.getString(1),restaurant.getId()));
        assertThat(evidence).contains(admin.getEmail(),"quota.dishes","Capacity exception for audit test");
        Map<String,Object> event=jdbc.queryForMap("select actor_id,before_json,after_json from entitlement_audit where restaurant_id=? order by id desc limit 1",restaurant.getId());
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(((Number)event.get("actor_id")).longValue()).isEqualTo(admin.getId());
        assertThat(json.readTree((String)event.get("before_json")).path("quota.dishes").asText()).isEqualTo("UNLIMITED");
        assertThat(json.readTree((String)event.get("after_json")).path("quota.dishes").asLong()).isEqualTo(3);
    }
    @Test void ownerProjectionDoesNotExposeOperatorReasonsOrPlatformAudit() {
        service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(3,version(),"SENSITIVE_TEST_OPERATOR_REASON",null),principal());
        Map<String,Object> owner=service.effective(restaurant.getId(),false);
        assertThat(owner).doesNotContainKeys("history","overrides");
        assertThat(owner.toString()).doesNotContain("SENSITIVE_TEST_OPERATOR_REASON",admin.getEmail());
    }
    @Test void brandingPermissionRevokedDuringDetachedHeadPreventsPublicationAndPreservesOldLogo() {
        String oldKey="restaurant-atelier/"+restaurant.getSlug()+"/branding/logo-existing.webp";
        String newKey="restaurant-atelier/"+restaurant.getSlug()+"/branding/logo-new.webp";
        tx.executeWithoutResult(s->{Restaurant current=restaurants.findById(restaurant.getId()).orElseThrow();current.setLogoS3Key(oldKey);current.setLogoUrl("https://example.test/existing-logo.webp");current.setLogoSizeBytes(100L);current.setStorageUsedBytes(100L);});
        when(storage.head(newKey)).thenAnswer(invocation->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            service.override(restaurant.getId(),"branding.enabled",new EntitlementService.OverrideWrite(false,version(),"Branding disabled during verification",null),principal());
            return software.amazon.awssdk.services.s3.model.HeadObjectResponse.builder().contentLength(100L).contentType("image/webp").build();
        });
        assertThatThrownBy(()->brandingService.confirmAsset(tenant(),"logo",new com.atelier.media.dto.BrandingAssetConfirmDto(newKey))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(storage).head(newKey);
        Restaurant after=restaurants.findById(restaurant.getId()).orElseThrow();
        assertThat(after.getLogoS3Key()).isEqualTo(oldKey);assertThat(after.getLogoUrl()).isEqualTo("https://example.test/existing-logo.webp");
        assertThat(after.getStorageUsedBytes()).isEqualTo(100L);
    }
    @Test void tenantRevokedDuringPrivateSigningCannotCommitIssuedReferenceKeys() {
        Media dish=TestFixtures.media(100,restaurant.getId(),restaurant.getSlug(),UploadStatus.COMPLETED);dish.setId(null);dish.setVersion(null);dish=media.saveAndFlush(dish);
        long dishId=dish.getId();Tenant captured=tenant();
        doAnswer(invocation->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            tx.executeWithoutResult(s->restaurants.findById(restaurant.getId()).orElseThrow().incrementTokenVersion());
            return new S3Storage.PresignedUpload("https://example.test/private-upload",Instant.now().plusSeconds(300),300,Map.of());
        }).when(storage).presignPut(anyString(),anyString(),anyLong());
        assertThatThrownBy(()->modelRequests.presignPhotos(captured,dishId,List.of(new com.atelier.media.dto.ModelRequestItemDto("front","reference.webp","image/webp",100L))))
                .isInstanceOf(com.atelier.media.exception.InvalidTokenException.class);
        assertThat(pendingKeys.findByRestaurantIdAndDishId(restaurant.getId(),dishId)).isEmpty();
        assertThat(media.findById(dishId).orElseThrow().getModelRequestStatus()).isEqualTo("NONE");
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getTokenVersion()).isEqualTo(captured.tokenVersion()+1);
    }
    @Test void adminRevokedDuringKeyAllocationCannotCommitPendingModelUpload() {
        Media dish=TestFixtures.media(100,restaurant.getId(),restaurant.getSlug(),UploadStatus.COMPLETED);dish.setId(null);dish.setVersion(null);dish=media.saveAndFlush(dish);
        long dishId=dish.getId();AdminPrincipal captured=principal();
        doAnswer(invocation->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            tx.executeWithoutResult(s->admins.findById(admin.getId()).orElseThrow().incrementTokenVersion());
            return "restaurant-atelier/"+restaurant.getSlug()+"/models/twin-revoked.glb";
        }).when(storage).newKey(anyString(),anyString(),anyString(),anyString());
        assertThatThrownBy(()->modelRequests.presignModelUpload(dishId,new com.atelier.media.dto.Attach3dRequestDto("twin.glb","model/gltf-binary",100L,"table"),captured))
                .isInstanceOf(com.atelier.media.exception.InvalidTokenException.class);
        Media after=media.findById(dishId).orElseThrow();assertThat(after.getPendingModelS3Key()).isNull();assertThat(after.getPendingModelCreatedAt()).isNull();
        verify(storage,never()).presignPut(anyString(),anyString(),anyLong());
        assertThat(admins.findById(admin.getId()).orElseThrow().getTokenVersion()).isEqualTo(captured.tokenVersion()+1);
    }
    @Test void effectiveReadSnapshotCannotMixPolicyValueAndVersionAcrossConcurrentOverride() throws Exception {
        assign(plan(Map.of("quota.dishes",1)));long initialVersion=version();
        var selected=new CountDownLatch(1);var finishRead=new CountDownLatch(1);
        var intercept=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(invocation->{
            Object result=invocation.callRealMethod();
            if(intercept.compareAndSet(true,false)) {
                selected.countDown();assertThat(finishRead.await(15,TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(entitlementDb).query(startsWith("select r.values_json"),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<String>>any(),any(Object[].class));
        var executor=Executors.newSingleThreadExecutor();
        try {
            Future<Map<String,Object>> snapshot=executor.submit(()->service.effective(restaurant.getId(),true));
            boolean paused=selected.await(15,TimeUnit.SECONDS);
            if(!paused && snapshot.isDone()) snapshot.get();
            assertThat(paused).as("reader must pause between policy value and metadata reads").isTrue();
            service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(2,initialVersion,"Concurrent change during overview read",null),principal());
            finishRead.countDown();Map<String,Object> before=snapshot.get(15,TimeUnit.SECONDS);
            @SuppressWarnings("unchecked") Map<String,Object> beforeValues=(Map<String,Object>)before.get("values");
            assertThat(((Number)beforeValues.get("quota.dishes")).longValue()).isOne();
            assertThat(((Number)before.get("configVersion")).longValue()).isEqualTo(initialVersion);
            assertThat(((Number)values(restaurant.getId()).get("quota.dishes")).longValue()).isEqualTo(2);
            assertThat(version()).isEqualTo(initialVersion+1);
        } finally {finishRead.countDown();executor.shutdownNow();}
    }
    @Test void planRevisionAuditRetainsExactMetadataAndRevisionIdentity() throws Exception {
        String suffix=UUID.randomUUID().toString();String initialName="Before "+suffix,updatedName="After "+suffix;
        Map<String,Object> first=service.createPlan(new EntitlementService.PlanWrite(initialName,"Original description",true,0,Map.of("quota.dishes",1)),principal());
        Map<String,Object> revised=service.revisePlan(number(first,"id"),new EntitlementService.PlanWrite(updatedName,"Revised description",false,number(first,"version"),Map.of("quota.dishes",2)),principal());
        Map<String,Object> event=jdbc.queryForMap("select before_json,after_json from entitlement_audit where plan_id=? and action='PLAN_REVISE' order by id desc limit 1",number(first,"id"));
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var before=json.readTree((String)event.get("before_json"));var after=json.readTree((String)event.get("after_json"));
        assertThat(before.path("name").asText()).isEqualTo(initialName);assertThat(after.path("name").asText()).isEqualTo(updatedName);
        assertThat(before.path("description").asText()).isEqualTo("Original description");assertThat(after.path("description").asText()).isEqualTo("Revised description");
        assertThat(before.path("active").asBoolean()).isTrue();assertThat(after.path("active").asBoolean()).isFalse();
        assertThat(before.path("latestRevisionId").asLong()).isEqualTo(number(first,"latestRevisionId"));
        assertThat(after.path("latestRevisionId").asLong()).isEqualTo(number(revised,"latestRevisionId"));
        assertThat(before.path("values").path("quota.dishes").asLong()).isOne();assertThat(after.path("values").path("quota.dishes").asLong()).isEqualTo(2);
    }
    @Test void planMutationDoesNotDependOnAppearingInFirstTwoHundredCatalogueRows() throws Exception {
        String prefix="A catalogue fixture "+UUID.randomUUID()+" ";
        String valueJson=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(FeatureRegistry.defaults());
        tx.executeWithoutResult(s->{
            for(int n=0;n<201;n++) {
                String name=prefix+n;
                jdbc.update("insert into entitlement_plans(name,description,active,version) values(?,'Catalogue fixture',true,1)",name);
                long id=jdbc.queryForObject("select id from entitlement_plans where name=?",Long.class,name);
                jdbc.update("insert into entitlement_plan_revisions(plan_id,revision,name,description,values_json) values(?,1,?,'Catalogue fixture',?)",id,name,valueJson);
            }
        });
        try {
            Map<String,Object> created=service.createPlan(new EntitlementService.PlanWrite("Z outside first page "+UUID.randomUUID(),"New plan outside list window",true,0,Map.of("quota.dishes",7)),principal());
            long id=number(created,"id");
            assertThat(service.plans()).noneMatch(p->number(p,"id")==id);
            Map<String,Object> revised=service.revisePlan(id,new EntitlementService.PlanWrite("Z edited outside first page "+UUID.randomUUID(),"Edit still succeeds",true,number(created,"version"),Map.of("quota.dishes",8)),principal());
            assertThat(number(revised,"id")).isEqualTo(id);assertThat(number(revised,"latestRevisionId")).isNotEqualTo(number(created,"latestRevisionId"));
        } finally {
            tx.executeWithoutResult(s->{
                jdbc.update("delete from entitlement_plan_revisions where plan_id in (select id from entitlement_plans where name like ?)",prefix+"%");
                jdbc.update("delete from entitlement_plans where name like ?",prefix+"%");
            });
        }
    }
    @Test void existingAdminModelPermissionControlChangesAssignedEffectiveGrant() {
        assign(plan(Map.of("model.ownerUpload",false)));
        adminService.update3dPermission(restaurant.getId(),true,principal());
        assertThat(values(restaurant.getId()).get("model.ownerUpload")).isEqualTo(true);
        adminService.update3dPermission(restaurant.getId(),false,principal());
        assertThat(values(restaurant.getId()).get("model.ownerUpload")).isEqualTo(false);
    }
    @Test void existingAdminProfileEditorDoesNotSilentlyIgnoreAssignedModelPermission() {
        assign(plan(Map.of("model.ownerUpload",false)));
        adminService.update(restaurant.getId(),new com.atelier.media.dto.admin.AdminRestaurantUpdateDto(
                restaurant.getName(),restaurant.getSlug(),restaurant.getOwnerName(),restaurant.getEmail(),null,true),principal());
        assertThat(values(restaurant.getId()).get("model.ownerUpload")).isEqualTo(true);
    }

    @Test void mandatoryAuditFailureRollsBackPolicyAndConfigurationVersion() {
        long beforeVersion=version(); Object beforeValue=values(restaurant.getId()).get("quota.dishes");
        long beforeAudit=jdbc.queryForObject("select count(*) from entitlement_audit where restaurant_id=?",Long.class,restaurant.getId());
        // Failure injection affects this disposable H2 schema only; no live schema is inspected or altered.
        tx.executeWithoutResult(status->jdbc.execute("alter table entitlement_audit add constraint ck_entitlement_test_audit_failure check (reason <> 'ROLLBACK_TEST_MARKER')"));
        try {
            assertThatThrownBy(()->service.override(restaurant.getId(),"quota.dishes",new EntitlementService.OverrideWrite(3,beforeVersion,"ROLLBACK_TEST_MARKER",null),principal())).isInstanceOf(RuntimeException.class);
        } finally {
            tx.executeWithoutResult(status->jdbc.execute("alter table entitlement_audit drop constraint ck_entitlement_test_audit_failure"));
        }
        assertThat(version()).isEqualTo(beforeVersion);assertThat(values(restaurant.getId()).get("quota.dishes")).isEqualTo(beforeValue);
        assertThat(jdbc.queryForObject("select count(*) from entitlement_audit where restaurant_id=?",Long.class,restaurant.getId())).isEqualTo(beforeAudit);
    }

    @Test void provisioningCommitsPinnedPlanAndInitialExceptionsWithRestaurant() {
        var plan=plan(Map.of("quota.dishes",10));String slug="provision-"+UUID.randomUUID();
        Map<String,Object> initial=new LinkedHashMap<>();initial.put("model.ownerUpload",true);initial.put("quota.dishes",3);
        var created=adminService.create(new com.atelier.media.dto.admin.AdminRestaurantCreateDto("Provisioned",slug,"Owner",slug+"@example.test",null,RestaurantStatus.ACTIVE,false,
                number(plan,"latestRevisionId"),initial),principal());
        long id=created.restaurant().id();
        assertThat(service.effective(id,true).get("revisionId")).isEqualTo(number(plan,"latestRevisionId"));
        assertThat(((Number)values(id).get("quota.dishes")).longValue()).isEqualTo(3);
        assertThat(values(id).get("model.ownerUpload")).isEqualTo(true);
        assertThat(service.history(id)).hasSize(3);
        for(var event:service.history(id))if("ENTITLEMENT_OVERRIDE".equals(event.get("action"))) {
            var after=(Map<?,?>)event.get("after");assertThat(((Number)after.get("quota.dishes")).longValue()).isEqualTo(3);assertThat(after.get("model.ownerUpload")).isEqualTo(true);
        }
    }
    @Test void invalidInitialPolicyRollsBackEntireRestaurantProvisioning() {
        String slug="rollback-provision-"+UUID.randomUUID(),email=slug+"@example.test";
        assertThatThrownBy(()->adminService.create(new com.atelier.media.dto.admin.AdminRestaurantCreateDto("Rollback",slug,"Owner",email,null,RestaurantStatus.ACTIVE,false,
                null,Map.of("menu.allowedModes",List.of("SIMPLE"))),principal())).isInstanceOf(RuntimeException.class);
        assertThat(restaurants.existsByEmail(email)).isFalse();
    }
    @Test void guestModesResolveFreshAfterOverrideAndNeverExposePolicyOrAuditInternals() {
        Media dish=TestFixtures.mediaWithModel(30000,restaurant.getId(),restaurant.getSlug());dish.setId(null);dish.setVersion(null);media.saveAndFlush(dish);
        assign(plan(Map.of("menu.allowedModes",List.of("SIMPLE","THREE_D"),"model.concierge",false)));
        var threeD=menus.getMenu(restaurant.getSlug());assertThat(threeD.allowedModes()).containsExactly("SIMPLE","THREE_D");
        assertThat(threeD.items().getFirst().is3d()).isTrue();assertThat(threeD.items().getFirst().hasArSupport()).isFalse();
        service.override(restaurant.getId(),"menu.allowedModes",new EntitlementService.OverrideWrite(List.of("SIMPLE"),version(),"Simple-only guest menu",null),principal());
        var simple=menus.getMenu(restaurant.getSlug());assertThat(simple.allowedModes()).containsExactly("SIMPLE");assertThat(simple.items().getFirst().modelUrl()).isNull();
        assertThat(simple.items().getFirst().is3d()).isFalse();assertThat(simple.items().getFirst().s3Key()).isNull();assertThat(media.findById(dish.getId())).isPresent();
    }

    private Map<String,Object> plan(Map<String,Object> values) {
        return service.createPlan(new EntitlementService.PlanWrite("Plan "+UUID.randomUUID(),"Synthetic test plan",true,0,values),principal());
    }
    private void assign(Map<String,Object> plan) {service.assign(restaurant.getId(),new EntitlementService.AssignmentWrite(number(plan,"latestRevisionId"),version(),"Explicit test assignment"),principal());}
    private long version(){return ((Number)service.effective(restaurant.getId(),true).get("configVersion")).longValue();}
    @SuppressWarnings("unchecked") private Map<String,Object> values(long id){return (Map<String,Object>)service.effective(id,true).get("values");}
    private static long number(Map<String,Object> map,String key){return ((Number)map.get(key)).longValue();}
    private AdminPrincipal principal(){return new AdminPrincipal(admin.getId(),admin.getEmail(),admin.getTokenVersion());}
    private Tenant tenant(){return new Tenant(restaurant.getId(),restaurant.getSlug(),false,restaurant.getTokenVersion());}
    private PresignRequestDto photo(){return new PresignRequestDto("dish.webp","image/webp","Synthetic dish",BigDecimal.ONE,DishCategory.MAINS,MediaType.IMAGE_2D,100L,100L,"table",null,List.of(),true);}
}
