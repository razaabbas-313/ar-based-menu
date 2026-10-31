package com.atelier.media.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Populated disposable V15 upgrade; no application configuration, network or cloud access. */
class GuestExperienceMigrationTest {
    @Test void v16AddsDisabledLegacySettingsWithoutChangingExistingAccountsOrEntitlements() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:guest_upgrade_"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration/h2").target("15").load().migrate();
        var db=new JdbcTemplate(ds);
        db.update("insert into restaurants(name,slug,owner_name,email,password_hash,can_upload_3d,status,dish_count,model_3d_count,storage_used_bytes) values('Legacy guest','legacy-guest','Owner','guest@example.test','test-only-placeholder',true,'ACTIVE',7,2,5100)");
        db.update("insert into restaurants(name,slug,owner_name,email,password_hash,can_upload_3d,status) values('Restricted guest','restricted-guest','Owner','restricted@example.test','test-only-placeholder',false,'SUSPENDED')");
        long tenant=db.queryForObject("select id from restaurants where slug='legacy-guest'",Long.class);
        db.update("insert into entitlement_plans(name,description,active) values('Pinned plan','Synthetic existing revision',true)");
        long plan=db.queryForObject("select id from entitlement_plans",Long.class);
        String values="{\"menu.allowedModes\":[\"SIMPLE\",\"THREE_D\",\"AR\"]}";
        db.update("insert into entitlement_plan_revisions(plan_id,revision,name,description,values_json) values(?,1,'Pinned plan','Existing',?)",plan,values);
        long revision=db.queryForObject("select id from entitlement_plan_revisions",Long.class);
        db.update("insert into restaurant_entitlement_policy(restaurant_id,revision_id,version) values(?,?,9)",tenant,revision);
        db.update("insert into restaurant_entitlement_overrides(restaurant_id,feature_key,value_json,reason,actor_id,starts_at) values(?,'qr.table','true','Synthetic existing grant',1,CURRENT_TIMESTAMP)",tenant);
        var after=Flyway.configure().dataSource(ds).locations("classpath:db/migration/h2").target("16").load();after.migrate();after.validate();
        assertThat(after.info().current().getVersion().toString()).isEqualTo("16");
        assertThat(db.queryForObject("select count(*) from restaurant_guest_settings where menu_mode='LEGACY' and ordering_enabled=false and waiter_enabled=false and version=0 and order_number is null and waiter_number is null",Long.class)).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from restaurant_tables",Long.class)).isZero();
        assertThat(db.queryForObject("select revision_id from restaurant_entitlement_policy where restaurant_id=?",Long.class,tenant)).isEqualTo(revision);
        assertThat(db.queryForObject("select version from restaurant_entitlement_policy where restaurant_id=?",Long.class,tenant)).isEqualTo(9);
        assertThat(db.queryForObject("select values_json from entitlement_plan_revisions where id=?",String.class,revision)).isEqualTo(values);
        assertThat(db.queryForObject("select value_json from restaurant_entitlement_overrides where restaurant_id=?",String.class,tenant)).isEqualTo("true");
        assertThat(db.queryForObject("select can_upload_3d from restaurants where id=?",Boolean.class,tenant)).isTrue();
        assertThat(db.queryForObject("select storage_used_bytes from restaurants where id=?",Long.class,tenant)).isEqualTo(5100);
        assertThat(db.queryForObject("select dish_count from restaurants where id=?",Integer.class,tenant)).isEqualTo(7);
        assertThat(db.queryForObject("select model_3d_count from restaurants where id=?",Integer.class,tenant)).isEqualTo(2);
        assertThat(db.queryForObject("select status from restaurants where slug='restricted-guest'",String.class)).isEqualTo("SUSPENDED");
        assertThat(db.queryForObject("select can_upload_3d from restaurants where slug='restricted-guest'",Boolean.class)).isFalse();
    }
}
