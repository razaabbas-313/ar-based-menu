package com.atelier.media.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.*;

/** Durable, exact-key cleanup. No tenant FK: intents must survive tenant removal. */
@Slf4j
@Service
public class DurableCleanup {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final S3Storage storage;
    private final com.atelier.media.config.AtelierProperties props;
    public DurableCleanup(JdbcTemplate db, TransactionTemplate tx, S3Storage storage, com.atelier.media.config.AtelierProperties props) {
        this.db=db; this.tx=tx; this.storage=storage; this.props=props;
    }
    public void record(long tenant, String bucket, String key, String reason) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            tx.executeWithoutResult(s -> record(tenant,bucket,key,reason)); return;
        }
        if (key==null || key.isBlank()) return;
        // A bucket/key has one durable disposition; duplicate releases never erase failure evidence.
        String slug=db.query("select slug from restaurants where id=?", (rs,n)->rs.getString(1),tenant).stream().findFirst().orElse("");
        Instant now=Instant.now();
        db.update("insert into s3_cleanup_tasks(id,restaurant_id,bucket,object_key,tenant_slug,reason,state,attempts,available_at,created_at) values(?,?,?,?,?,?,'READY',0,?,?) on conflict do nothing",
                UUID.randomUUID().toString(),tenant,bucket,key,slug,reason,Timestamp.from(now.plus(props.presignTtl()).plus(Duration.ofHours(1))),Timestamp.from(now));
        if("TENANT_BRAND_REMOVED".equals(reason))db.update("update s3_cleanup_tasks set state='READY',reason='TENANT_BRAND_REMOVED',available_at=? where bucket=? and object_key=? and state='HELD' and reason='RETAINED_BRAND_IMAGE'",Timestamp.from(now.plus(props.presignTtl()).plus(Duration.ofHours(1))),bucket,key);
        if("RETAINED_MODEL".equals(reason)) db.update("update s3_cleanup_tasks set reason='RETAINED_MODEL',state='HELD' where bucket=? and object_key=?",bucket,key);
    }
    public boolean isRetired(String key) {
        return db.queryForObject("select count(*) from s3_cleanup_tasks where object_key=?",Long.class,key)>0;
    }
    record Task(String id,long tenant,String bucket,String key,String slug,int attempts,String token) {}
    /** CAS claims, fenced settlements and bounded leases permit recovery after a process dies. */
    @Scheduled(fixedDelayString="${atelier.cleanup-interval:PT1M}", initialDelayString="PT2M")
    public void runBatch() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Cleanup worker needs detached transaction boundary");
        Instant now=Instant.now();
        List<String> ids=db.query("select id from s3_cleanup_tasks where ((state in ('READY','RETRY') and available_at<=?) or (state='LEASED' and lease_until<=?)) order by created_at limit 100",
                (rs,n)->rs.getString(1),Timestamp.from(now),Timestamp.from(now));
        List<Task> claimed=new ArrayList<>();
        for(String id:ids) {
            Task task=tx.execute(s -> {
                String token=UUID.randomUUID().toString();
                int updated=db.update("update s3_cleanup_tasks set state='LEASED',lease_token=?,lease_until=?,attempts=attempts+1 where id=? and ((state in ('READY','RETRY') and available_at<=?) or (state='LEASED' and lease_until<=?))",
                        token,Timestamp.from(now.plus(Duration.ofMinutes(15))),id,Timestamp.from(now),Timestamp.from(now));
                if(updated==0) return null;
                return db.queryForObject("select * from s3_cleanup_tasks where id=?",(rs,n)->new Task(id,rs.getLong("restaurant_id"),rs.getString("bucket"),rs.getString("object_key"),rs.getString("tenant_slug"),rs.getInt("attempts"),token),id);
            });
            if(task==null) continue;
            if(!eligible(task)) { settle(task,"HELD","PROTECTED_OR_UNVERIFIED",null); continue; }
            claimed.add(task);
        }
        Map<String,List<Task>> buckets=new HashMap<>();
        claimed.forEach(t -> buckets.computeIfAbsent(t.bucket(),k->new ArrayList<>()).add(t));
        for(var entry:buckets.entrySet()) {
            // Legacy reference scans can be slow. Never issue DELETE using an expired or stolen claim.
            Instant dispatch=Instant.now();
            List<Task> owned=entry.getValue().stream().filter(t -> Boolean.TRUE.equals(tx.execute(s ->
                    db.update("update s3_cleanup_tasks set lease_until=? where id=? and state='LEASED' and lease_token=? and lease_until>?",
                            Timestamp.from(dispatch.plus(Duration.ofMinutes(15))),t.id(),t.token(),Timestamp.from(dispatch))==1))).toList();
            if(owned.isEmpty()) continue;
            S3DeletionResult result;
            Set<String> keys=new HashSet<>(); owned.forEach(t->keys.add(t.key()));
            try { result=storage.deleteKeys(entry.getKey(),keys); }
            catch(RuntimeException e) { result=new S3DeletionResult(Set.of(),Map.of(),keys,true); }
            if(result==null) result=new S3DeletionResult(Set.of(),Map.of(),keys,true);
            for(Task t:owned) {
                boolean acknowledged=result.deleted().contains(t.key()) && !result.failed().containsKey(t.key()) && !result.unknown().contains(t.key());
                String state=acknowledged?"DONE":t.attempts()>=8?"MANUAL":"RETRY";
                settle(t,state,acknowledged?"ACKNOWLEDGED":result.failed().containsKey(t.key())?"FAILED":"UNKNOWN",result.failed().get(t.key()));
            }
        }
    }
    private void settle(Task t,String state,String outcome,String error) {
        Instant now=Instant.now();
        String safeError=error!=null && error.matches("[A-Za-z0-9_]{1,64}")?error:null;
        tx.executeWithoutResult(s -> db.update("update s3_cleanup_tasks set state=?,last_outcome=?,last_error_code=?,available_at=?,lease_token=null,lease_until=null where id=? and state='LEASED' and lease_token=?",
                state,outcome,safeError,Timestamp.from(now.plusSeconds(Math.min(86400,60L << Math.min(10,t.attempts())))),t.id(),t.token()));
        if(!"DONE".equals(state)) log.warn("Cleanup task {} tenant={} state={} attempt={} outcome={}",t.id(),t.tenant(),state,t.attempts(),outcome);
    }
    private boolean eligible(Task t) {
        if(!Objects.equals(storage.bucket(),t.bucket()) || t.slug().isBlank() || t.key().contains("..") || t.key().contains("?") ||
                !(t.key().startsWith(storage.tenantPrefix(t.slug())) || t.key().startsWith(storage.privateTenantPrefix(t.slug())))) return false;
        if(db.queryForObject("select count(*) from s3_cleanup_tasks where object_key=? and reason='RETAINED_MODEL'",Long.class,t.key())>0) return false;
        long live=db.queryForObject("select count(*) from media_details where (s3_key=? and upload_status<>'FAILED') or model_s3_key=? or pending_model_s3_key=? or pending_image_s3_key=?",Long.class,t.key(),t.key(),t.key(),t.key());
        live+=db.queryForObject("select count(*) from restaurants where logo_s3_key=? or banner_s3_key=?",Long.class,t.key(),t.key());
        live+=db.queryForObject("select count(*) from model_request_pending_keys where s3_key=?",Long.class,t.key());
        // Models are retained by history regardless of status. No new retention/purge policy is inferred.
        live+=db.queryForObject("select count(*) from model_requests where completed_model_key=?",Long.class,t.key());
        live+=db.queryForObject("select count(*) from model_versions where object_key=? and retention='HELD'",Long.class,t.key());
        live+=db.queryForObject("select count(*) from brand_studio_assets where object_key=? and (state='VERIFIED' or (state='ISSUED' and expires_at>CURRENT_TIMESTAMP))",Long.class,t.key());
        if(live>0) return false;
        // Parse exact keys: LIKE/substring checks can mistake keys embedded in URLs and cannot establish ownership.
        if(referencesContain("media_details","model_request_photos",t.key()) || referencesContain("model_requests","reference_photos",t.key())) return false;
        return true;
    }
    private boolean referencesContain(String table,String column,String key) {
        // Identifiers are fixed internal constants; values remain parameters. Bound memory while examining legacy JSON.
        long after=0;
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        while(true) {
            var rows=db.query("select id,"+column+" from "+table+" where id>? and "+column+" is not null order by id limit 100",
                    (rs,n)->new Object[]{rs.getLong(1),rs.getString(2)},after);
            if(rows.isEmpty()) return false;
            for(var row:rows) {
                after=(Long)row[0];String json=(String)row[1];
                if(json.isBlank())continue;
                try {var root=mapper.readTree(json);if(!root.isArray())return true;
                    for(var node:root)if(key.equals(node.path("s3Key").asText()))return true;
                } catch(Exception e){return true;}
            }
        }
    }

}
