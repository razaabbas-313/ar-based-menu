package com.atelier.media.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** A real V14 database is populated before V15 runs; never imports application/cloud configuration. */
class EntitlementMigrationTest {
    @Test void upgradeFromV14PreservesLegacyGrantsAssetsUsageAndBackfillsSubmissionLedger() {
        String url="jdbc:h2:mem:entitlement_upgrade_"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var dataSource=new DriverManagerDataSource(url,"sa","");
        var before=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/h2").target("14").load();
        before.migrate();
        assertThat(before.info().current().getVersion().toString()).isEqualTo("14");
        var db=new JdbcTemplate(dataSource);
        db.update("insert into restaurants(name,slug,owner_name,email,password_hash,can_upload_3d,status,dish_count,model_3d_count,storage_used_bytes) values('Legacy granted','legacy-granted','Owner','granted@example.test','test-only-placeholder',true,'ACTIVE',1,1,5100)");
        db.update("insert into restaurants(name,slug,owner_name,email,password_hash,can_upload_3d,status) values('Legacy restricted','legacy-restricted','Owner','restricted@example.test','test-only-placeholder',false,'SUSPENDED')");
        long granted=db.queryForObject("select id from restaurants where slug='legacy-granted'",Long.class);
        long restricted=db.queryForObject("select id from restaurants where slug='legacy-restricted'",Long.class);
        String key="restaurant-atelier/legacy-granted/image-2d/legacy.webp";
        db.update("insert into media_details(restaurant_id,original_file_name,stored_file_name,s3_bucket,s3_key,s3_url,content_type,file_extension,file_size_bytes,media_type,upload_status,dish_title,price_tag,dish_category,completed_at,dish_code,model_s3_key,model_file_size_bytes) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                granted,"legacy.webp","legacy.webp","atelier-test-bucket",key,"https://example.test/legacy.webp","image/webp","webp",100L,"IMAGE_2D","COMPLETED","Legacy dish",BigDecimal.ONE,"MAINS",Timestamp.from(Instant.now()),"DSH-0001","restaurant-atelier/legacy-granted/model-3d/legacy.glb",5000L);
        long dish=db.queryForObject("select id from media_details where s3_key=?",Long.class,key);
        Instant currentMonth=YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant oldMonth=YearMonth.now(ZoneOffset.UTC).minusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        db.update("insert into model_requests(restaurant_id,dish_id,status,request_type,requested_at) values(?,?,'COMPLETED','NEW',?)",granted,dish,Timestamp.from(currentMonth.plusSeconds(1)));
        db.update("insert into model_requests(restaurant_id,dish_id,status,request_type,requested_at) values(?,?,'REJECTED','REPLACEMENT',?)",granted,dish,Timestamp.from(currentMonth.plusSeconds(2)));
        db.update("insert into model_requests(restaurant_id,dish_id,status,request_type,requested_at) values(?,?,'SUPERSEDED','REPLACEMENT',?)",granted,dish,Timestamp.from(oldMonth));
        String cleanupId=UUID.randomUUID().toString();
        db.update("insert into s3_cleanup_tasks(id,restaurant_id,bucket,object_key,tenant_slug,reason,state,attempts,available_at,created_at) values(?,?,?,?,?,?,'READY',0,?,?)",
                cleanupId,granted,"atelier-test-bucket","restaurant-atelier/legacy-granted/old.webp","legacy-granted","LEGACY_UPGRADE_TEST",Timestamp.from(Instant.now().plusSeconds(3600)),Timestamp.from(Instant.now()));

        var after=Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/h2").target("15").load();
        after.migrate();
        assertThat(after.info().current().getVersion().toString()).isEqualTo("15");
        assertThat(db.queryForObject("select count(*) from restaurant_entitlement_policy where revision_id is null and version=0",Long.class)).isEqualTo(2);
        assertThat(db.queryForObject("select can_upload_3d from restaurants where id=?",Boolean.class,granted)).isTrue();
        assertThat(db.queryForObject("select can_upload_3d from restaurants where id=?",Boolean.class,restricted)).isFalse();
        assertThat(db.queryForObject("select status from restaurants where id=?",String.class,restricted)).isEqualTo("SUSPENDED");
        assertThat(db.queryForObject("select dish_count from restaurants where id=?",Integer.class,granted)).isOne();
        assertThat(db.queryForObject("select model_3d_count from restaurants where id=?",Integer.class,granted)).isOne();
        assertThat(db.queryForObject("select storage_used_bytes from restaurants where id=?",Long.class,granted)).isEqualTo(5100);
        assertThat(db.queryForObject("select s3_key from media_details where id=?",String.class,dish)).isEqualTo(key);
        assertThat(db.queryForObject("select model_s3_key from media_details where id=?",String.class,dish)).endsWith("/legacy.glb");
        assertThat(db.queryForObject("select count(*) from entitlement_request_usage where restaurant_id=?",Long.class,granted)).isEqualTo(3);
        assertThat(db.queryForObject("select count(*) from entitlement_request_usage where restaurant_id=? and submitted_at>=?",Long.class,granted,Timestamp.from(currentMonth))).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from entitlement_request_usage u join model_requests r on u.operation_id=concat('legacy-',cast(r.id as varchar)) where u.restaurant_id=r.restaurant_id and u.dish_id=r.dish_id and u.submitted_at=r.requested_at",Long.class)).isEqualTo(3);
        assertThat(db.queryForObject("select state from s3_cleanup_tasks where id=?",String.class,cleanupId)).isEqualTo("READY");
        assertThat(db.queryForObject("select count(*) from entitlement_plans",Long.class)).isZero();
        after.validate();
    }
}
