package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.*;
import com.atelier.media.exception.*;
import com.atelier.media.repository.*;
import com.atelier.media.security.Tenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

/** Uses the existing direct upload signer; verification never publishes a draft image. */
@Service
public class BrandDraftAssets {
    private final EntitlementRepository db;private final RestaurantRepository restaurants;private final RestaurantAccess access;
    private final EntitlementService entitlements;private final S3Storage storage;private final AtelierProperties props;private final TransactionTemplate tx;private final DurableCleanup cleanup;
    private final com.github.benmanes.caffeine.cache.Cache<Long,java.util.concurrent.atomic.AtomicInteger> seals=com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(1)).build();
    public BrandDraftAssets(EntitlementRepository db,RestaurantRepository restaurants,RestaurantAccess access,EntitlementService entitlements,S3Storage storage,AtelierProperties props,TransactionTemplate tx,DurableCleanup cleanup){this.db=db;this.restaurants=restaurants;this.access=access;this.entitlements=entitlements;this.storage=storage;this.props=props;this.tx=tx;this.cleanup=cleanup;}
    public Map<String,Object> issue(Tenant tenant,String purpose,BrandingAssetPresignDto dto){
        if(!Set.of("LOGO","BANNER").contains(purpose))throw new InvalidFieldException("purpose","Choose LOGO or BANNER.");
        var r=access.requireStudioAccess(tenant);entitlements.requireFeature(r.getId(),"branding.enabled");storage.requireConfigured();
        String type=MediaRules.normalizeContentType(dto.contentType()),extension=MediaRules.requireImage(type);MediaRules.requireCompatibleExtension(MediaRules.sanitizeFilename(dto.filename()),type);MediaRules.requireMaxSize(dto.uploadSizeBytes(),props.maxBrandingBytes(),"brand images");
        String id=UUID.randomUUID().toString(),key=storage.privateTenantPrefix(r.getSlug())+"branding/uploads/"+UUID.randomUUID()+"."+extension;
        tx.executeWithoutResult(s->{lock(tenant);entitlements.requireFeature(r.getId(),"branding.enabled");Long issued=db.queryForObject("select count(*) from brand_studio_assets where restaurant_id=? and state in ('ISSUED','VERIFIED')",Long.class,r.getId());if(issued>=100)throw new ConflictException("assets","The retained draft asset safety limit is 100. Contact your administrator for retention review.");db.update("insert into brand_studio_assets(id,restaurant_id,object_key,upload_key,object_url,purpose,expected_bytes,content_type,expires_at) values(?,?,?,?,?,?,?,?,?)",id,r.getId(),key,key,"/api/v1/public/brand-assets/"+id,purpose,dto.uploadSizeBytes(),type,Timestamp.from(Instant.now().plus(props.presignTtl()).plus(Duration.ofHours(1))));});
        var signed=storage.presignPut(key,type,dto.uploadSizeBytes());return Map.of("id",id,"uploadUrl",signed.url(),"objectKey",key,"requiredHeaders",signed.requiredHeaders());
    }
    public Map<String,Object> verify(Tenant tenant,String id){
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Draft verification requires a detached transaction boundary");
        var r=access.requireStudioAccess(tenant);entitlements.requireFeature(tenant.restaurantId(),"branding.enabled");var issued=owned(tenant.restaurantId(),id);if("VERIFIED".equals(issued.get("state")))return safe(issued);
        var expiresBeforeCopy=db.queryForObject("select expires_at from brand_studio_assets where id=?",java.time.OffsetDateTime.class,id);
        if(!"ISSUED".equals(issued.get("state"))||!expiresBeforeCopy.toInstant().isAfter(Instant.now()))throw new UploadVerificationException("This draft image upload expired.");
        if(seals.get(tenant.restaurantId(),k->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet()>10)throw new TooManyAttemptsException(Duration.ofMinutes(1));
        String source=(String)issued.get("object_key");var head=storage.head(source);
        if(!Objects.equals(head.contentLength(),((Number)issued.get("expected_bytes")).longValue())||!Objects.equals(MediaRules.normalizeContentType(head.contentType()),issued.get("content_type"))||head.eTag()==null||head.eTag().isBlank())throw new UploadVerificationException("Image metadata does not match its issued upload.");
        String sealed=storage.privateTenantPrefix(r.getSlug())+"branding/sealed/"+UUID.randomUUID()+"."+MediaRules.requireImage((String)issued.get("content_type"));
        cleanup.record(tenant.restaurantId(),storage.bucket(),sealed,"DRAFT_SEAL_INTENT");
        try{
            storage.sealPrivateCopy(source,sealed,head.eTag(),(String)issued.get("content_type"));
            return tx.execute(s->{
                lock(tenant);entitlements.requireFeature(tenant.restaurantId(),"branding.enabled");var current=owned(tenant.restaurantId(),id);
                if("VERIFIED".equals(current.get("state"))){cleanup.record(tenant.restaurantId(),storage.bucket(),sealed,"ABANDONED_DRAFT_SEAL");return safe(current);}
                var expires=db.queryForObject("select expires_at from brand_studio_assets where id=?",java.time.OffsetDateTime.class,id);
                if(!"ISSUED".equals(current.get("state"))||!expires.toInstant().isAfter(Instant.now()))throw new UploadVerificationException("This draft image upload expired.");
                String disposition=db.queryForObject("select state from s3_cleanup_tasks where bucket=? and object_key=? for update",String.class,storage.bucket(),sealed);
                if(!"READY".equals(disposition))throw new UploadVerificationException("This sealed image is no longer available for publication.");
                db.update("update s3_cleanup_tasks set state='HELD',reason='RETAINED_BRAND_IMAGE' where bucket=? and object_key=?",storage.bucket(),sealed);
                db.update("update brand_studio_assets set state='VERIFIED',object_key=? where id=?",sealed,id);restaurants.adjustUsage(tenant.restaurantId(),((Number)current.get("expected_bytes")).longValue(),0,0);
                cleanup.record(tenant.restaurantId(),storage.bucket(),source,"DRAFT_UPLOAD_RELEASED");current.put("object_key",sealed);return safe(current);
            });
        }catch(RuntimeException error){cleanup.record(tenant.restaurantId(),storage.bucket(),sealed,"ABANDONED_DRAFT_SEAL");throw error;}
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="PT10M",initialDelayString="PT10M") public void expire(){
        var ids=db.queryForList("select id from brand_studio_assets where state='ISSUED' and expires_at<CURRENT_TIMESTAMP order by created_at limit 100",String.class);
        for(String id:ids)tx.executeWithoutResult(s->{var rows=db.queryForList("select * from brand_studio_assets where id=?",id);if(rows.isEmpty())return;var row=rows.getFirst();long tenant=((Number)row.get("restaurant_id")).longValue();if(restaurants.findByIdForUpdate(tenant).isEmpty())return;if(db.update("update brand_studio_assets set state='EXPIRED' where id=? and state='ISSUED' and expires_at<CURRENT_TIMESTAMP",id)==1)cleanup.record(tenant,storage.bucket(),(String)row.get("object_key"),"EXPIRED_DRAFT_IMAGE");});
    }
    private void lock(Tenant t){restaurants.findByIdForUpdate(t.restaurantId()).orElseThrow(()->new InvalidTokenException("Restaurant unavailable."));access.requireStudioAccess(t);}
    private Map<String,Object> owned(long tenant,String id){var rows=db.queryForList("select * from brand_studio_assets where restaurant_id=? and id=?",tenant,id);if(rows.isEmpty())throw new InvalidFieldException("asset","Draft image not found in your restaurant.");return rows.getFirst();}
    private Map<String,Object> safe(Map<String,Object> row){return Map.of("id",row.get("id"),"url",storage.presignPrivateGet((String)row.get("object_key"),Duration.ofMinutes(15)),"purpose",row.get("purpose"));}
}
