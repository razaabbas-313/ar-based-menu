package com.atelier.media.service;

import com.atelier.media.exception.ConflictException;
import com.atelier.media.exception.InvalidFieldException;
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
import com.atelier.media.dto.BrandingAssetPresignDto;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual disposable H2 transactions; no storage or production configuration access. */
@SpringBootTest(properties={
        "spring.config.import=optional:classpath:product-studio-no-secrets.properties",
        "spring.datasource.url=jdbc:h2:mem:product_studio;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.locations=classpath:db/migration/h2","atelier.scheduling.enabled=false",
        "aws.access-key-id=AKIAIOSFODNN7EXAMPLE","aws.secret-access-key=synthetic-test-secret-not-for-aws",
        "aws.region=us-east-1","aws.s3.bucket=atelier-test-bucket"})
@ActiveProfiles("test")
class ProductStudioIntegrationTest {
    @Autowired ProductStudioService service;
    @Autowired EntitlementService entitlements;
    @Autowired RestaurantRepository restaurants;
    @Autowired PlatformUserRepository admins;
    @Autowired TransactionTemplate tx;
    @Autowired BrandDraftAssets assets;
    @Autowired DurableCleanup cleanup;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @MockBean S3Storage storage;
    Restaurant restaurant,other;
    PlatformUser admin;
    private static final String REVIEW="https://g.page/r/Synthetic_place-123/review";

    @BeforeEach void seed(){
        restaurant=create("product-");other=create("other-product-");
        admin=admins.saveAndFlush(PlatformUser.builder().email(UUID.randomUUID()+"@platform.test").displayName("Product fixture operator")
                .passwordHash("synthetic-test-placeholder").mustChangePassword(false).build());
        when(storage.bucket()).thenReturn("atelier-test-bucket");
        when(storage.tenantPrefix(anyString())).thenAnswer(i->"restaurant-atelier/"+i.getArgument(0)+"/");
        when(storage.privateTenantPrefix(anyString())).thenAnswer(i->"restaurant-atelier-private/"+i.getArgument(0)+"/");
        when(storage.newKey(anyString(),eq("branding"),anyString(),anyString())).thenAnswer(i->"restaurant-atelier/"+i.getArgument(0)+"/branding/"+UUID.randomUUID()+".webp");
        when(storage.publicUrl(anyString())).thenAnswer(i->"https://example.test/"+i.getArgument(0));
        when(storage.presignPut(anyString(),anyString(),anyLong())).thenReturn(new S3Storage.PresignedUpload("https://example.test/synthetic-upload",Instant.now().plusSeconds(600),600,Map.of("Content-Type","image/webp")));
        when(storage.head(anyString())).thenReturn(HeadObjectResponse.builder().contentLength(500L).contentType("image/webp").eTag("synthetic-valid-etag").build());
        when(storage.presignPrivateGet(anyString(),any(java.time.Duration.class))).thenAnswer(i->preview(i.getArgument(0)));
        when(storage.deleteKeys(anyString(),anyCollection())).thenAnswer(i->new S3DeletionResult(new HashSet<>((Collection<String>)i.getArgument(1)),Map.of(),Set.of(),true));
    }
    @Test void defaultsPreserveLegacyAppearanceAndDisableReviews(){
        var view=service.appearance(tenant());assertThat(version(view)).isZero();
        assertThat(view.get("published")).isInstanceOf(Map.class);
        assertThat(((Map<?,?>)view.get("published")).get("preset")).isEqualTo("LEGACY");
        var review=service.reviewSettings(tenant());assertThat(version(review)).isZero();
        assertThat(review.get("enabled")).isEqualTo(false);assertThat(review.get("url")).isNull();
    }
    @Test void draftIsPrivateUntilPublishAndDiscardRestoresPublishedValues(){
        var draft=service.saveDraft(tenant(),0,theme());assertThat(version(draft)).isEqualTo(1);
        assertThat(service.publicAppearance(restaurant.getSlug()).toString()).doesNotContain("#2255aa");
        var published=service.publish(tenant(),1);assertThat(version(published)).isEqualTo(2);assertThat(published.get("publishedAt")).isNotNull();
        assertThat(service.publicAppearance(restaurant.getSlug()).toString()).contains("#2255aa");
        var changed=new HashMap<>(theme());changed.put("accent","#cc2200");service.saveDraft(tenant(),2,changed);
        assertThat(service.publicAppearance(restaurant.getSlug()).toString()).doesNotContain("#cc2200");
        var discarded=service.discard(tenant(),3);assertThat(version(discarded)).isEqualTo(4);
        assertThat(discarded.get("draft")).isEqualTo(discarded.get("published"));
    }
    @Test void staleDraftPublishAndDiscardCannotOverwriteCurrentVersion(){
        service.saveDraft(tenant(),0,theme());
        assertThatThrownBy(()->service.saveDraft(tenant(),0,theme())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(()->service.publish(tenant(),0)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(()->service.discard(tenant(),0)).isInstanceOf(ConflictException.class);
        assertThat(version(service.appearance(tenant()))).isEqualTo(1);
    }
    @Test void premiumRevocationMasksCustomValuesEvenWhenPresetClaimsLegacy(){
        var custom=new HashMap<>(theme());custom.put("preset","LEGACY");
        service.saveDraft(tenant(),0,custom);service.publish(tenant(),1);
        assertThat(service.publicAppearance(restaurant.getSlug()).toString()).contains("#2255aa");
        grant("branding.premiumOptions",false);
        assertThat(service.publicAppearance(restaurant.getSlug()).toString()).doesNotContain("#2255aa");
        assertThat(((Map<?,?>)service.appearance(tenant()).get("published")).get("accent")).isEqualTo("#2255aa");
    }
    @Test void missingRequiredHeadingCannotBeHiddenByOptionalLogoAsset(){
        var issued=issue(tenant(),"LOGO");assets.verify(tenant(),(String)issued.get("id"));
        var invalid=new HashMap<>(theme());invalid.remove("heading");invalid.put("logoAsset",(String)issued.get("id"));
        assertThatThrownBy(()->service.saveDraft(tenant(),0,invalid)).isInstanceOf(InvalidFieldException.class);
        assertThat(version(service.appearance(tenant()))).isZero();
    }
    @Test void themeSchemaRejectsUnknownKeysCssPayloadsInvalidEnumsAndUnreadableContrast(){
        for(var mutation:List.of(Map.entry("preset","INVENTED"),Map.entry("accent","url(https://example.test/a)"),
                Map.entry("heading","Comic Sans"),Map.entry("layout","absolute"),Map.entry("unknownCss","position:fixed"),
                Map.entry("text","#ffffff"))){
            var invalid=new HashMap<>(theme());invalid.put(mutation.getKey(),mutation.getValue());
            assertThatThrownBy(()->service.saveDraft(tenant(),0,invalid)).as(mutation.getKey()).isInstanceOf(InvalidFieldException.class);
        }
        assertThat(version(service.appearance(tenant()))).isZero();
    }
    @Test void brandingAndPremiumEntitlementsAreEnforcedWithoutDeletingPublishedState(){
        service.saveDraft(tenant(),0,theme());service.publish(tenant(),1);grant("branding.premiumOptions",false);
        assertThatThrownBy(()->service.saveDraft(tenant(),2,theme())).isInstanceOf(RuntimeException.class);
        grant("branding.enabled",false);
        assertThatThrownBy(()->service.publish(tenant(),2)).isInstanceOf(RuntimeException.class);
        assertThat(service.publicAppearance(restaurant.getSlug()).toString()).doesNotContain("#2255aa");
        grant("branding.enabled",true);grant("branding.premiumOptions",true);
        assertThat(((Map<?,?>)service.appearance(tenant()).get("published")).get("accent")).isEqualTo("#2255aa");
    }
    @Test void appearanceAndReviewStateAreIsolatedBetweenRestaurants(){
        service.saveDraft(tenant(),0,theme());service.publish(tenant(),1);
        assertThat(version(service.appearance(otherTenant()))).isZero();
        assertThat(service.publicAppearance(other.getSlug()).toString()).doesNotContain("#2255aa");
        grant("reviews.enabled",true);service.saveReview(tenant(),0,REVIEW,true);
        assertThat(service.reviewSettings(otherTenant()).get("url")).isNull();
        assertThat(service.publicAppearance(other.getSlug()).toString()).doesNotContain(REVIEW);
    }
    @Test void unavailableAccountsCannotMutateProductStudio(){
        tx.executeWithoutResult(s->restaurants.findById(restaurant.getId()).orElseThrow().incrementTokenVersion());
        assertThatThrownBy(()->service.saveDraft(tenant(),0,theme())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->service.saveReview(tenant(),0,REVIEW,false)).isInstanceOf(RuntimeException.class);
        tx.executeWithoutResult(s->restaurants.findById(other.getId()).orElseThrow().setStatus(RestaurantStatus.SUSPENDED));
        assertThatThrownBy(()->service.saveDraft(otherTenant(),0,theme())).isInstanceOf(RuntimeException.class);
    }
    @Test void reviewEnableRequiresEntitlementAndRevocationImmediatelyHidesPublishedLink(){
        assertThatThrownBy(()->service.saveReview(tenant(),0,REVIEW,true)).isInstanceOf(RuntimeException.class);
        grant("reviews.enabled",true);var saved=service.saveReview(tenant(),0,REVIEW,true);assertThat(version(saved)).isEqualTo(1);
        assertThat(service.publicAppearance(restaurant.getSlug()).toString()).contains(REVIEW);
        grant("reviews.enabled",false);assertThat(service.publicAppearance(restaurant.getSlug()).toString()).doesNotContain(REVIEW);
        assertThat(service.reviewSettings(tenant()).get("url")).isEqualTo(REVIEW);
        assertThat(service.saveReview(tenant(),1,null,false).get("enabled")).isEqualTo(false);
    }
    @Test void reviewWritesUseOptimisticVersionAndDoNotOverwriteOnFailure(){
        grant("reviews.enabled",true);service.saveReview(tenant(),0,REVIEW,true);
        assertThatThrownBy(()->service.saveReview(tenant(),0,null,false)).isInstanceOf(ConflictException.class);
        assertThat(service.reviewSettings(tenant()).get("url")).isEqualTo(REVIEW);
    }
    @Test void reviewUrlOnlyAcceptsExactApprovedHttpsFormats(){
        assertThat(ProductStudioService.reviewUrl(REVIEW)).isEqualTo(REVIEW);
        String search="https://search.google.com/local/writereview?placeid=ChIJ_synthetic-123";
        assertThat(ProductStudioService.reviewUrl(search)).isEqualTo(search);
        for(String invalid:List.of("http://g.page/r/place/review","https://g.page.evil.test/r/place/review",
                "https://g.page@evil.test/r/place/review","https://evil.test@g.page/r/place/review",
                "https://g.page:443/r/place/review","https://g.page/r/place/review?x=1","https://g.page/r/place/review#x",
                "https://g.page/r/place/review/","https://g.page/r/%2e%2e/review","javascript:alert(1)",
                "https://search.google.com/local/writereview?placeid=ok&next=https://evil.test",
                "https://search.google.com/local/writereview?placeid=one&placeid=two","https://search.google.com/local/writereview?placeid=",
                "https://search.google.com/local/writereview?placeid=place#evil"))
            assertThatThrownBy(()->ProductStudioService.reviewUrl(invalid)).as(invalid).isInstanceOf(InvalidFieldException.class);
    }
    @Test void verifiedImageDoesNotPublishUntilExplicitAppearancePublication(){
        var issued=issue(tenant(),"LOGO");var verified=assets.verify(tenant(),(String)issued.get("id"));
        String url=(String)verified.get("url");
        assertThat(service.publicAppearance(restaurant.getSlug()).toString()).doesNotContain(url);
        var values=new HashMap<>(theme());values.put("logoAsset",(String)issued.get("id"));
        service.saveDraft(tenant(),0,values);
        assertThat(service.publicAppearance(restaurant.getSlug()).toString()).doesNotContain(url);
        service.publish(tenant(),1);
        var publicView=service.publicAppearance(restaurant.getSlug());
        assertThat(((Map<?,?>)publicView.get("appearance")).get("logoUrl")).isEqualTo("/api/v1/public/brand-assets/"+issued.get("id"));
        assertThat(publicView.toString()).doesNotContain(url);
        assertThat((Map<String,Object>)publicView.get("appearance")).doesNotContainKeys("logoAsset","objectKey","bannerAsset");
    }
    @Test void foreignUnverifiedAndWrongPurposeAssetIdsCannotEnterDraft(){
        var foreign=issue(otherTenant(),"LOGO");assets.verify(otherTenant(),(String)foreign.get("id"));
        var pending=issue(tenant(),"LOGO");var banner=issue(tenant(),"BANNER");assets.verify(tenant(),(String)banner.get("id"));
        for(var issued:List.of(foreign,pending,banner)){
            var values=new HashMap<>(theme());values.put("logoAsset",(String)issued.get("id"));
            assertThatThrownBy(()->service.saveDraft(tenant(),0,values)).isInstanceOf(InvalidFieldException.class);
        }
        assertThatThrownBy(()->assets.verify(tenant(),(String)foreign.get("id"))).isInstanceOf(InvalidFieldException.class);
        assertThat(version(service.appearance(tenant()))).isZero();
    }
    @Test void repeatedImageVerificationChargesStorageOnlyOnce(){
        long before=restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes();
        var issued=issue(tenant(),"LOGO");String id=(String)issued.get("id");
        assets.verify(tenant(),id);assets.verify(tenant(),id);
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(before+500);
        verify(storage,times(1)).head((String)issued.get("objectKey"));
    }
    @Test void imageMetadataMismatchCannotVerifyOrChargeStorage(){
        var issued=issue(tenant(),"LOGO");String key=(String)issued.get("objectKey");long before=restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes();
        when(storage.head(key)).thenReturn(HeadObjectResponse.builder().contentLength(501L).contentType("image/webp").build());
        assertThatThrownBy(()->assets.verify(tenant(),(String)issued.get("id"))).isInstanceOf(com.atelier.media.exception.UploadVerificationException.class);
        when(storage.head(key)).thenReturn(HeadObjectResponse.builder().contentLength(500L).contentType("image/png").build());
        assertThatThrownBy(()->assets.verify(tenant(),(String)issued.get("id"))).isInstanceOf(com.atelier.media.exception.UploadVerificationException.class);
        assertThat(db.queryForObject("select state from brand_studio_assets where id=?",String.class,issued.get("id"))).isEqualTo("ISSUED");
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(before);
    }
    @Test void ownerRevocationDuringHeadPreventsVerificationCommit(){
        var issued=issue(tenant(),"LOGO");long before=restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes();
        when(storage.head((String)issued.get("objectKey"))).thenAnswer(i->{
            tx.executeWithoutResult(s->restaurants.findById(restaurant.getId()).orElseThrow().incrementTokenVersion());
            return HeadObjectResponse.builder().contentLength(500L).contentType("image/webp").eTag("synthetic-valid-etag").build();
        });
        assertThatThrownBy(()->assets.verify(tenant(),(String)issued.get("id"))).isInstanceOf(com.atelier.media.exception.InvalidTokenException.class);
        assertThat(db.queryForObject("select state from brand_studio_assets where id=?",String.class,issued.get("id"))).isEqualTo("ISSUED");
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(before);
    }
    @Test void cleanupRetainsBothPublishedAndUnpublishedSelectedImages() throws Exception {
        var logo=issue(tenant(),"LOGO");assets.verify(tenant(),(String)logo.get("id"));
        var published=new HashMap<>(theme());published.put("logoAsset",(String)logo.get("id"));
        var banner=issue(tenant(),"BANNER");assets.verify(tenant(),(String)banner.get("id"));
        var draft=new HashMap<>(published);draft.put("bannerAsset",(String)banner.get("id"));
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        // Seed a valid existing selection independently of the pending draft-image hook.
        String publishedJson=json.writeValueAsString(published),draftJson=json.writeValueAsString(draft);
        tx.executeWithoutResult(s->db.update("insert into restaurant_appearance(restaurant_id,version,published_json,draft_json,published_at) values(?,3,?,?,CURRENT_TIMESTAMP)",restaurant.getId(),publishedJson,draftJson));
        for(var image:List.of(logo,banner)){
            String key=sealedKey(image);
            cleanup.record(restaurant.getId(),"atelier-test-bucket",key,"SUPERSEDED_BRAND_IMAGE");
            tx.executeWithoutResult(s->db.update("update s3_cleanup_tasks set available_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP) where object_key=?",key));
        }
        cleanup.runBatch();
        for(var image:List.of(logo,banner))assertThat(db.queryForObject("select state from s3_cleanup_tasks where object_key=?",String.class,sealedKey(image))).isEqualTo("HELD");
        verify(storage,never()).deleteKeys(anyString(),anyCollection());
    }
    @Test void entitlementRevocationDuringHeadPreventsVerificationCommit(){
        var issued=issue(tenant(),"LOGO");long before=restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes();
        when(storage.head((String)issued.get("objectKey"))).thenAnswer(i->{
            grant("branding.enabled",false);
            return HeadObjectResponse.builder().contentLength(500L).contentType("image/webp").eTag("synthetic-valid-etag").build();
        });
        assertThatThrownBy(()->assets.verify(tenant(),(String)issued.get("id"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(db.queryForObject("select state from brand_studio_assets where id=?",String.class,issued.get("id"))).isEqualTo("ISSUED");
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(before);
    }
    @Test void retainedAssetSafetyCapRejectsFurtherIssuanceWithoutEviction(){
        tx.executeWithoutResult(s->{for(int i=0;i<100;i++)db.update("insert into brand_studio_assets(id,restaurant_id,object_key,upload_key,object_url,purpose,expected_bytes,content_type,expires_at) values(?,?,?,?,?,?,?,?,DATEADD('HOUR',1,CURRENT_TIMESTAMP))",
                UUID.randomUUID().toString(),restaurant.getId(),"restaurant-atelier-private/"+restaurant.getSlug()+"/branding/uploads/cap-"+i+".webp","restaurant-atelier-private/"+restaurant.getSlug()+"/branding/uploads/cap-"+i+".webp","/api/v1/public/brand-assets/synthetic-cap-"+i,"LOGO",500,"image/webp");});
        assertThatThrownBy(()->issue(tenant(),"LOGO")).isInstanceOf(ConflictException.class);
        assertThat(db.queryForObject("select count(*) from brand_studio_assets where restaurant_id=?",Long.class,restaurant.getId())).isEqualTo(100);
        verify(storage,never()).presignPut(anyString(),anyString(),anyLong());
        verify(storage,never()).deleteKeys(anyString(),anyCollection());
    }
    @Test void expiredImageIssuanceCannotVerifyOrChargeStorage(){
        var issued=issue(tenant(),"LOGO");long before=restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes();
        tx.executeWithoutResult(s->db.update("update brand_studio_assets set expires_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP) where id=?",issued.get("id")));
        assertThatThrownBy(()->assets.verify(tenant(),(String)issued.get("id"))).isInstanceOf(com.atelier.media.exception.UploadVerificationException.class);
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(before);
        verify(storage,never()).sealPrivateCopy(anyString(),anyString(),anyString(),anyString());
    }
    @Test void privateUploadIsConditionallySealedToFreshNeverPutSignedObject(){
        var issued=issue(tenant(),"LOGO");String original=(String)issued.get("objectKey");
        assertThat(original).startsWith("restaurant-atelier-private/"+restaurant.getSlug()+"/branding/uploads/");
        var result=assets.verify(tenant(),(String)issued.get("id"));String sealed=sealedKey(issued);
        assertThat(sealed).startsWith("restaurant-atelier-private/"+restaurant.getSlug()+"/branding/").isNotEqualTo(original);
        verify(storage).sealPrivateCopy(original,sealed,"synthetic-valid-etag","image/webp");
        verify(storage,never()).presignPut(eq(sealed),anyString(),anyLong());
        assertThat(db.queryForObject("select upload_key from brand_studio_assets where id=?",String.class,issued.get("id"))).isEqualTo(original);
        assertThat(db.queryForObject("select object_url from brand_studio_assets where id=?",String.class,issued.get("id"))).isEqualTo("/api/v1/public/brand-assets/"+issued.get("id"));
        assertThat(result.get("url")).isEqualTo(preview(sealed));
        assertThat(service.appearance(tenant()).get("assets").toString()).contains(preview(sealed));
        var values=new HashMap<>(theme());values.put("logoAsset",(String)issued.get("id"));service.saveDraft(tenant(),0,values);service.publish(tenant(),1);
        // A late browser overwrite can affect only the issued upload key, never the sealed published reference.
        when(storage.head(original)).thenReturn(HeadObjectResponse.builder().contentLength(999L).contentType("image/png").eTag("late-put-etag").build());
        assets.verify(tenant(),(String)issued.get("id"));
        assertThat(sealedKey(issued)).isEqualTo(sealed);assertThat(service.publicAssetUrl((String)issued.get("id"))).isEqualTo(preview(sealed));
        verify(storage,times(1)).sealPrivateCopy(anyString(),anyString(),anyString(),anyString());
    }
    @Test void publicAssetBrokerRequiresCurrentPublishedReferenceAndLiveEntitlements(){
        var first=issue(tenant(),"LOGO");assets.verify(tenant(),(String)first.get("id"));String firstId=(String)first.get("id");
        assertThatThrownBy(()->service.publicAssetUrl(firstId)).isInstanceOf(RuntimeException.class);
        var values=new HashMap<>(theme());values.put("logoAsset",firstId);service.saveDraft(tenant(),0,values);
        assertThatThrownBy(()->service.publicAssetUrl(firstId)).isInstanceOf(RuntimeException.class);
        service.publish(tenant(),1);assertThat(service.publicAssetUrl(firstId)).isEqualTo(preview(sealedKey(first)));
        var second=issue(tenant(),"LOGO");assets.verify(tenant(),(String)second.get("id"));String secondId=(String)second.get("id");
        values.put("logoAsset",secondId);service.saveDraft(tenant(),2,values);
        assertThatThrownBy(()->service.publicAssetUrl(secondId)).isInstanceOf(RuntimeException.class);
        service.discard(tenant(),3);assertThatThrownBy(()->service.publicAssetUrl(secondId)).isInstanceOf(RuntimeException.class);
        service.saveDraft(tenant(),4,values);service.publish(tenant(),5);
        assertThatThrownBy(()->service.publicAssetUrl(firstId)).isInstanceOf(RuntimeException.class);
        assertThat(service.publicAssetUrl(secondId)).isEqualTo(preview(sealedKey(second)));
        grant("branding.enabled",false);assertThatThrownBy(()->service.publicAssetUrl(secondId)).isInstanceOf(RuntimeException.class);
        grant("branding.enabled",true);grant("branding.premiumOptions",false);
        assertThatThrownBy(()->service.publicAssetUrl(secondId)).isInstanceOf(RuntimeException.class);
        grant("branding.premiumOptions",true);tx.executeWithoutResult(s->restaurants.findById(restaurant.getId()).orElseThrow().setIsMenuLive(false));
        assertThatThrownBy(()->service.publicAssetUrl(secondId)).isInstanceOf(RuntimeException.class);
    }
    @Test void missingOrBlankHeadEtagCannotVerifyOrChargeStorage(){
        var issued=issue(tenant(),"LOGO");String key=(String)issued.get("objectKey");long before=restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes();
        for(String etag:Arrays.asList(null,"","   ")){
            when(storage.head(key)).thenReturn(HeadObjectResponse.builder().contentLength(500L).contentType("image/webp").eTag(etag).build());
            assertThatThrownBy(()->assets.verify(tenant(),(String)issued.get("id"))).isInstanceOf(com.atelier.media.exception.UploadVerificationException.class);
        }
        assertThat(db.queryForObject("select state from brand_studio_assets where id=?",String.class,issued.get("id"))).isEqualTo("ISSUED");
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(before);
        verify(storage,never()).sealPrivateCopy(anyString(),anyString(),anyString(),anyString());
    }
    @Test void failedConditionalSealCannotVerifyOrChargeStorage(){
        var issued=issue(tenant(),"LOGO");long before=restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes();
        doThrow(new IllegalStateException("Synthetic conditional copy failure")).when(storage).sealPrivateCopy(anyString(),anyString(),anyString(),anyString());
        assertThatThrownBy(()->assets.verify(tenant(),(String)issued.get("id"))).isInstanceOf(RuntimeException.class);
        assertThat(db.queryForObject("select state from brand_studio_assets where id=?",String.class,issued.get("id"))).isEqualTo("ISSUED");
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(before);
        assertThatThrownBy(()->service.publicAssetUrl((String)issued.get("id"))).isInstanceOf(RuntimeException.class);
    }
    @Test void durableReadySealIntentIsCommittedBeforeCopyAndHeldOnAttachment(){
        var issued=issue(tenant(),"LOGO");
        doAnswer(invocation->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var rows=db.queryForList("select state,reason,restaurant_id from s3_cleanup_tasks where bucket=? and object_key=?","atelier-test-bucket",invocation.getArgument(1));
            assertThat(rows).hasSize(1);assertThat(rows.getFirst().get("state")).isEqualTo("READY");
            assertThat(rows.getFirst().get("reason")).isEqualTo("DRAFT_SEAL_INTENT");
            assertThat(((Number)rows.getFirst().get("restaurant_id")).longValue()).isEqualTo(restaurant.getId());
            return null;
        }).when(storage).sealPrivateCopy(anyString(),anyString(),anyString(),anyString());
        assets.verify(tenant(),(String)issued.get("id"));
        var intent=db.queryForMap("select state,reason from s3_cleanup_tasks where object_key=?",sealedKey(issued));
        assertThat(intent.get("state")).isEqualTo("HELD");assertThat(intent.get("reason")).isEqualTo("RETAINED_BRAND_IMAGE");
    }
    @Test void simulatedAbruptCopyExitLeavesDurableIntentWithoutVerificationOrCharge(){
        var issued=issue(tenant(),"LOGO");long before=restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes();
        var destination=new java.util.concurrent.atomic.AtomicReference<String>();
        var abrupt=new AssertionError("Synthetic abrupt flow at copy boundary; not an actual process kill");
        doAnswer(invocation->{destination.set(invocation.getArgument(1));throw abrupt;})
                .when(storage).sealPrivateCopy(anyString(),anyString(),anyString(),anyString());
        assertThatThrownBy(()->assets.verify(tenant(),(String)issued.get("id"))).isSameAs(abrupt);
        assertThat(destination.get()).isNotNull();
        var intents=db.queryForList("select state,reason from s3_cleanup_tasks where object_key=?",destination.get());
        assertThat(intents).hasSize(1);assertThat(intents.getFirst().get("state")).isEqualTo("READY");
        assertThat(intents.getFirst().get("reason")).isEqualTo("DRAFT_SEAL_INTENT");
        assertThat(db.queryForObject("select state from brand_studio_assets where id=?",String.class,issued.get("id"))).isEqualTo("ISSUED");
        assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(before);
    }
    @Test void cleanupClaimOrCompletedDeletionPreventsLateSealAttachment(){
        long before=restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes();
        for(String state:List.of("LEASED","DONE")){
            var issued=issue(tenant(),"LOGO");
            doAnswer(invocation->{tx.executeWithoutResult(s->db.update("update s3_cleanup_tasks set state=? where object_key=?",state,invocation.getArgument(1)));return null;})
                    .when(storage).sealPrivateCopy(anyString(),anyString(),anyString(),anyString());
            assertThatThrownBy(()->assets.verify(tenant(),(String)issued.get("id"))).isInstanceOf(com.atelier.media.exception.UploadVerificationException.class);
            assertThat(db.queryForObject("select state from brand_studio_assets where id=?",String.class,issued.get("id"))).isEqualTo("ISSUED");
            assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getStorageUsedBytes()).isEqualTo(before);
        }
    }
    private String sealedKey(Map<String,Object> issued){return db.queryForObject("select object_key from brand_studio_assets where id=?",String.class,issued.get("id"));}
    private static String preview(String key){return "https://example.test/private-preview/"+key;}
    private Map<String,Object> issue(Tenant owner,String purpose){return assets.issue(owner,purpose,new BrandingAssetPresignDto("synthetic.webp","image/webp",500L));}
    private Restaurant create(String prefix){var r=TestFixtures.restaurant(1,prefix+UUID.randomUUID());r.setId(null);return restaurants.saveAndFlush(r);}
    private Tenant tenant(){return new Tenant(restaurant.getId(),restaurant.getSlug(),false,restaurant.getTokenVersion());}
    private Tenant otherTenant(){return new Tenant(other.getId(),other.getSlug(),false,other.getTokenVersion());}
    private static long version(Map<String,Object> view){return ((Number)view.get("version")).longValue();}
    private void grant(String key,Object value){long v=((Number)entitlements.effective(restaurant.getId(),true).get("configVersion")).longValue();
        entitlements.override(restaurant.getId(),key,new EntitlementService.OverrideWrite(value,v,"Synthetic product studio grant",null),new AdminPrincipal(admin.getId(),admin.getEmail(),admin.getTokenVersion()));}
    private static Map<String,String> theme(){return Map.ofEntries(Map.entry("preset","MINIMAL"),Map.entry("accent","#2255aa"),Map.entry("background","#ffffff"),Map.entry("surface","#f5f5f5"),Map.entry("text","#111111"),Map.entry("heading","SANS"),Map.entry("layout","cards"),Map.entry("category","pills"),Map.entry("button","rounded"),Map.entry("motion","subtle"),Map.entry("card","soft"));}
}
