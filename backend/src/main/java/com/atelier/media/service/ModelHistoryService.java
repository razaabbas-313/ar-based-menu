package com.atelier.media.service;

import com.atelier.media.model.Media;
import com.atelier.media.repository.EntitlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** Append-only asset provenance. No automatic purge is authorized. */
@Service
public class ModelHistoryService {
    private final EntitlementRepository db;
    public ModelHistoryService(EntitlementRepository db){this.db=db;}
    public void published(Media before,String key,String url,long bytes,String origin,String actorKind,Long actorId,Long requestId){
        requireTransaction();
        db.queryForObject("select id from restaurants where id=? for update",Long.class,before.getRestaurantId());
        retainLegacy(before);
        if(db.queryForObject("select count(*) from model_versions where dish_id=? and object_key=?",Long.class,before.getId(),key)>0)return;
        db.update("update model_versions set state='SUPERSEDED' where dish_id=? and state='CURRENT'",before.getId());
        long version=db.queryForObject("select coalesce(max(version_number),0)+1 from model_versions where dish_id=?",Long.class,before.getId());
        db.update("insert into model_versions(restaurant_id,dish_id,version_number,object_key,object_url,size_bytes,origin,actor_kind,actor_id,request_id,published_at,state,availability) values(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,'CURRENT','METADATA_VERIFIED')",
            before.getRestaurantId(),before.getId(),version,key,url,bytes,origin,actorKind,actorId,requestId);
        db.update("update model_versions set issued_actor_kind=(select actor_kind from model_upload_issuances where object_key=?),issued_actor_id=(select actor_id from model_upload_issuances where object_key=?),issued_at=(select created_at from model_upload_issuances where object_key=?) where dish_id=? and object_key=?",key,key,key,before.getId(),key);
    }
    public void issued(Media dish,String key,String actorKind,Long actorId){requireTransaction();db.queryForObject("select id from restaurants where id=? for update",Long.class,dish.getRestaurantId());db.update("insert into model_upload_issuances(object_key,restaurant_id,dish_id,actor_kind,actor_id) values(?,?,?,?,?)",key,dish.getRestaurantId(),dish.getId(),actorKind,actorId);}
    public void requireIssuer(String key,String kind,Long actorId){var rows=db.queryForList("select actor_kind,actor_id from model_upload_issuances where object_key=?",key);if(rows.isEmpty())return;var row=rows.getFirst();Long id=row.get("actor_id")==null?null:((Number)row.get("actor_id")).longValue();if(!Objects.equals(kind,row.get("actor_kind"))||!Objects.equals(id,actorId))throw new org.springframework.security.access.AccessDeniedException("This pending upload was issued to another actor.");}
    public void detached(Media before){requireTransaction();retainLegacy(before);db.update("update model_versions set state='SUPERSEDED' where dish_id=? and state='CURRENT'",before.getId());}
    private void retainLegacy(Media before){
        String key=before.getModelS3Key();if(key==null)return;
        long count=db.queryForObject("select count(*) from model_versions where dish_id=? and object_key=?",Long.class,before.getId(),key);
        if(count==0){long version=db.queryForObject("select coalesce(max(version_number),0)+1 from model_versions where dish_id=?",Long.class,before.getId());
            db.update("insert into model_versions(restaurant_id,dish_id,version_number,object_key,object_url,size_bytes,origin,state) values(?,?,?,?,?,?, 'LEGACY_UNKNOWN','CURRENT')",before.getRestaurantId(),before.getId(),version,key,before.getModelS3Url(),before.getModelFileSizeBytes()==null?0:before.getModelFileSizeBytes());}
    }
    public List<Map<String,Object>> list(long tenant,long dish,long after){
        return db.query("select * from model_versions where restaurant_id=? and dish_id=? and id>? order by id limit 100",(rs,n)->{
            Map<String,Object> row=new LinkedHashMap<>();for(String c:List.of("id","version_number","origin","actor_kind","actor_id","issued_actor_kind","issued_actor_id","issued_at","request_id","created_at","published_at","state","retention","availability","size_bytes"))row.put(c,rs.getObject(c));row.put("url",rs.getString("object_url"));return row;
        },tenant,dish,Math.max(0,after));
    }
    private void requireTransaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Model history requires the publication transaction");}
}
