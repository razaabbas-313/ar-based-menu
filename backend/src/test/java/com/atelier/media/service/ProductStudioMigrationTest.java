package com.atelier.media.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Populated V16→V17 on a unique disposable H2 database, without application/cloud startup. */
class ProductStudioMigrationTest {
    @Test void upgradePreservesLegacyProductDataAndBackfillsOnlyTruthfulHeldModelVersions(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:studio_upgrade_"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        var before=Flyway.configure().dataSource(ds).locations("classpath:db/migration/h2").target("16").load();before.migrate();
        assertThat(before.info().current().getVersion().toString()).isEqualTo("16");
        var db=new JdbcTemplate(ds);
        db.update("insert into restaurants(name,slug,owner_name,email,password_hash,status,can_upload_3d,theme_accent,menu_layout,logo_url,logo_s3_key,logo_size_bytes,banner_url,banner_s3_key,banner_size_bytes,dish_count,model_3d_count,storage_used_bytes) values('Existing product','existing-product','Owner','existing@example.test','synthetic-placeholder','ACTIVE',true,'emerald','compact','https://example.test/logo.webp','restaurant-atelier/existing-product/branding/logo.webp',100,'https://example.test/banner.webp','restaurant-atelier/existing-product/branding/banner.webp',200,3,2,6800)");
        db.update("insert into restaurants(name,slug,owner_name,email,password_hash,status,can_upload_3d) values('Restricted product','restricted-product','Owner','restricted@example.test','synthetic-placeholder','SUSPENDED',false)");
        long tenant=db.queryForObject("select id from restaurants where slug='existing-product'",Long.class);
        db.update("insert into entitlement_plans(name,description,active) values('Existing plan','Synthetic persisted plan',true)");
        long plan=db.queryForObject("select id from entitlement_plans",Long.class);
        db.update("insert into entitlement_plan_revisions(plan_id,revision,name,description,values_json) values(?,2,'Existing plan','Pinned revision','{\"branding.enabled\":true}')",plan);
        long revision=db.queryForObject("select id from entitlement_plan_revisions",Long.class);
        db.update("insert into restaurant_entitlement_policy(restaurant_id,revision_id,version) values(?,?,7)",tenant,revision);
        db.update("insert into restaurant_entitlement_overrides(restaurant_id,feature_key,value_json,reason,actor_id,starts_at) values(?,'reviews.enabled','true','Synthetic existing grant',1,CURRENT_TIMESTAMP)",tenant);
        db.update("insert into restaurant_guest_settings(restaurant_id,version,menu_mode,ordering_enabled,order_number) values(?,4,'THREE_D',true,'923001234567')",tenant);
        db.update("insert into restaurant_tables(restaurant_id,name,normalized_name,qr_token,token_hash) values(?,'Existing table','existing table',?,?)",tenant,"x".repeat(43),"a".repeat(64));
        long photoModel=insertMedia(db,tenant,"photo-model","IMAGE_2D","restaurant-atelier/existing-product/photos/dish.webp","restaurant-atelier/existing-product/models/dish.glb",5000);
        long modelOnly=insertMedia(db,tenant,"model-only","MODEL_3D","restaurant-atelier/existing-product/models/legacy.glb",null,0);
        insertMedia(db,tenant,"photo-only","IMAGE_2D","restaurant-atelier/existing-product/photos/only.webp",null,0);
        db.update("insert into analytics_events(restaurant_id,dish_id,event_type) values(?,?,'DISH_VIEW')",tenant,photoModel);
        db.update("insert into analytics_events(restaurant_id,event_type) values(?,'MENU_VIEW')",tenant);
        db.update("insert into analytics_daily(restaurant_id,dish_id,event_type,\"day\",event_count) values(?,0,'MENU_VIEW',DATE '2026-10-30',31)",tenant);
        var preserved=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("restaurants","media_details","entitlement_plans","entitlement_plan_revisions","restaurant_entitlement_policy","restaurant_entitlement_overrides","restaurant_guest_settings","restaurant_tables","analytics_events","analytics_daily"))
            preserved.put(table,db.queryForList("select * from "+table));

        var after=Flyway.configure().dataSource(ds).locations("classpath:db/migration/h2").target("17").load();after.migrate();after.validate();
        assertThat(after.info().current().getVersion().toString()).isEqualTo("17");
        preserved.forEach((table,rows)->assertThat(db.queryForList("select * from "+table)).as("Preserve existing %s rows",table).containsExactlyInAnyOrderElementsOf(rows));
        assertThat(db.queryForObject("select count(*) from model_versions",Long.class)).isEqualTo(2);
        assertThat(db.queryForObject("select object_key from model_versions where dish_id=?",String.class,photoModel)).isEqualTo("restaurant-atelier/existing-product/models/dish.glb");
        assertThat(db.queryForObject("select size_bytes from model_versions where dish_id=?",Long.class,photoModel)).isEqualTo(5000);
        assertThat(db.queryForObject("select object_key from model_versions where dish_id=?",String.class,modelOnly)).isEqualTo("restaurant-atelier/existing-product/models/legacy.glb");
        assertThat(db.queryForObject("select size_bytes from model_versions where dish_id=?",Long.class,modelOnly)).isEqualTo(500);
        assertThat(db.queryForObject("select count(*) from model_versions where origin='LEGACY_UNKNOWN' and state='CURRENT' and retention='HELD' and availability='UNVERIFIED' and version_number=1 and actor_kind is null and actor_id is null and request_id is null and published_at is null and issued_actor_kind is null and issued_actor_id is null and issued_at is null",Long.class)).isEqualTo(2);
        for(String table:List.of("guest_insight_events","analytics_legacy_dedup","brand_studio_assets","model_upload_issuances","restaurant_appearance","restaurant_reviews"))
            assertThat(db.queryForObject("select count(*) from "+table,Long.class)).as("No fabricated %s data",table).isZero();
    }
    private long insertMedia(JdbcTemplate db,long tenant,String name,String type,String key,String model,long modelSize){
        db.update("insert into media_details(restaurant_id,original_file_name,stored_file_name,s3_bucket,s3_key,s3_url,content_type,file_extension,file_size_bytes,media_type,upload_status,completed_at,dish_title,price_tag,dish_category,dish_code,model_s3_key,model_s3_url,model_file_size_bytes) values(?,?,?,?,?,?,?,?,?,?,'COMPLETED',TIMESTAMP WITH TIME ZONE '2026-10-30 12:00:00+00',?,18.00,'MAINS',?,?,?,?)",
                tenant,name,name,"atelier-test-bucket",key,"https://example.test/"+name,"MODEL_3D".equals(type)?"model/gltf-binary":"image/webp","MODEL_3D".equals(type)?"glb":"webp",500L,type,name,"CODE-"+name,model,model==null?null:"https://example.test/dish.glb",modelSize);
        return db.queryForObject("select id from media_details where s3_key=?",Long.class,key);
    }
}
