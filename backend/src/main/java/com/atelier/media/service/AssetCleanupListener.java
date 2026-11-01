package com.atelier.media.service;

import com.atelier.media.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** JPA lifecycle releases use the same JDBC transaction; bulk SQL bypasses this listener. */
@Component
public class AssetCleanupListener {
    private final DurableCleanup cleanup;
    private final JdbcTemplate db;
    private final String bucket;
    public AssetCleanupListener(@org.springframework.context.annotation.Lazy DurableCleanup cleanup, JdbcTemplate db, org.springframework.core.env.Environment environment) {
        this.cleanup=cleanup;this.db=db;this.bucket=environment.getProperty("aws.s3.bucket", "");
    }
    @PostLoad @PostPersist @PostUpdate
    public void snapshot(Object entity) {
        if(entity instanceof Media m) {
            try { m.setCleanupSnapshot(keys(m)); }
            catch(IllegalStateException malformed) { m.setCleanupSnapshot(null); }
        }
        if(entity instanceof Restaurant r) r.setCleanupSnapshot(keys(r));
    }
    @PreUpdate
    public void changed(Object entity) {
        if(entity instanceof Media m) release(m.getRestaurantId(),m.getS3Bucket(),m.getCleanupSnapshot(),keys(m),"MEDIA_RELEASE");
        if(entity instanceof Restaurant r && !java.util.Objects.equals(r.getCleanupSnapshot(),keys(r))) release(r.getId(),bucket,r.getCleanupSnapshot(),keys(r),"BRANDING_RELEASE");
    }
    @PreRemove
    public void removed(Object entity) {
        if(entity instanceof Media m) {
            db.update("update model_versions set state='SUPERSEDED' where dish_id=? and restaurant_id=? and state='CURRENT'",m.getId(),m.getRestaurantId());
            Set<String> known=keys(m);add(known,m.getS3Key());
            release(m.getRestaurantId(),m.getS3Bucket(),known,Set.of(),"DISH_REMOVED");
            captureRelated(m.getRestaurantId(),m.getId());
        }
        if(entity instanceof Restaurant r) {
            release(r.getId(),bucket,keys(r),Set.of(),"TENANT_REMOVED");
            db.query("select s3_bucket,s3_key,model_s3_key,pending_model_s3_key,pending_image_s3_key,model_request_photos from media_details where restaurant_id=?", rs -> {
                for(int i=2;i<=5;i++) cleanup.record(r.getId(),rs.getString(1),rs.getString(i),"TENANT_REMOVED");
                for(String key:photos(rs.getString(6))) cleanup.record(r.getId(),rs.getString(1),key,"TENANT_REMOVED");
            },r.getId());
            db.query("select object_key from brand_studio_assets where restaurant_id=?",rs->{cleanup.record(r.getId(),bucket,rs.getString(1),"TENANT_BRAND_REMOVED");},r.getId());
            captureRelated(r.getId(),null);
        }
    }
    private void captureRelated(long tenant,Long dish) {
        String filter=dish==null?"restaurant_id=?":"dish_id=?"; long id=dish==null?tenant:dish;
        db.query("select completed_model_key,reference_photos from model_requests where "+filter,rs -> {
            cleanup.record(tenant,bucket,rs.getString(1),"RETAINED_MODEL");
            for(String key:photos(rs.getString(2))) cleanup.record(tenant,bucket,key,"REFERENCE_REMOVED");
        },id);
        db.query("select s3_key from model_request_pending_keys where "+filter,rs -> {
            cleanup.record(tenant,bucket,rs.getString(1),"PENDING_REMOVED");
        },id);
    }
    private void release(long tenant,String bucket,Set<String> before,Set<String> after,String reason) {
        if(before==null) throw new IllegalStateException("Missing or malformed asset snapshot requires manual review");
        for(String key:after) if(!before.contains(key) && cleanup.isRetired(key)) throw new IllegalStateException("Retired asset cannot be published again");
        for(String key:before) if(!after.contains(key)) cleanup.record(tenant,bucket,key,reason);
    }
    private static Set<String> keys(Media m) {
        Set<String> keys=new HashSet<>();
        if(m.getUploadStatus()!=UploadStatus.FAILED) add(keys,m.getS3Key());
        add(keys,m.getModelS3Key());add(keys,m.getPendingModelS3Key());add(keys,m.getPendingImageS3Key());
        keys.addAll(photos(m.getModelRequestPhotos()));return keys;
    }
    private static Set<String> keys(Restaurant r) {
        Set<String> keys=new HashSet<>();add(keys,r.getLogoS3Key());add(keys,r.getBannerS3Key());return keys;
    }
    private static void add(Set<String> keys,String key) {if(key!=null&&!key.isBlank())keys.add(key);}
    static Set<String> photos(String json) {
        Set<String> keys=new HashSet<>();if(json==null||json.isBlank())return keys;
        try {var root=new ObjectMapper().readTree(json); if(!root.isArray())throw new IllegalArgumentException();
            for(var node:root)add(keys,node.path("s3Key").asText(null));
        } catch(Exception e) {throw new IllegalStateException("Malformed asset references require manual repair");}
        return keys;
    }
}
