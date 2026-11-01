package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.exception.*;
import com.atelier.media.model.*;
import com.atelier.media.repository.*;
import com.atelier.media.security.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Fresh policy resolution. All tenant mutations use the restaurant row as their common lock. */
@Service
public class EntitlementService {
    public record PlanWrite(String name,String description,boolean active,long expectedVersion,Map<String,Object> values) {}
    public record AssignmentWrite(long revisionId,long expectedVersion,String reason) {}
    public record OverrideWrite(Object value,long expectedVersion,String reason,Instant expiresAt) {}
    private final EntitlementRepository db;
    private final RestaurantRepository restaurants;
    private final AdminAccess admins;
    private final AdminAuditLogRepository audit;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final TransactionTemplate readSnapshot;
    private final AtelierProperties props;
    private final MenuCache cache;
    public EntitlementService(EntitlementRepository db,RestaurantRepository restaurants,AdminAccess admins,
            AdminAuditLogRepository audit,TransactionTemplate tx,ObjectMapper json,AtelierProperties props,MenuCache cache) {
        this.db=db;this.restaurants=restaurants;this.admins=admins;this.audit=audit;this.tx=tx;this.json=json;this.props=props;this.cache=cache;
        this.readSnapshot=new TransactionTemplate(Objects.requireNonNull(tx.getTransactionManager()));
        readSnapshot.setReadOnly(true);readSnapshot.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        readSnapshot.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public List<Map<String,Object>> registry() {
        return Arrays.stream(FeatureRegistry.Feature.values()).map(f->{Map<String,Object> m=new LinkedHashMap<>();
            m.put("key",f.key);m.put("label",f.label);m.put("type",f.kind);m.put("implemented",f.implemented);m.put("defaultValue",f.defaultValue);m.put("maximum",f.max);return m;}).toList();
    }
    public List<Map<String,Object>> plans() { return planRows(null); }
    private List<Map<String,Object>> planRows(Long id) {
        return db.query("select p.*,r.id as revision_id,r.values_json from entitlement_plans p join entitlement_plan_revisions r on r.plan_id=p.id and r.revision=p.version "+(id==null?"":"where p.id=? ")+"order by p.name,p.id limit 200",
                (rs,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong("id"));m.put("name",rs.getString("name"));m.put("description",rs.getString("description"));m.put("active",rs.getBoolean("active"));m.put("version",rs.getLong("version"));m.put("latestRevisionId",rs.getLong("revision_id"));m.put("values",read(rs.getString("values_json")));return m;},id==null?new Object[]{}:new Object[]{id});
    }
    public List<Map<String,Object>> revisions(long plan) {
        return db.query("select id,revision,name,description,values_json,created_at from entitlement_plan_revisions where plan_id=? order by revision desc limit 200",(rs,n)->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong(1));m.put("revision",rs.getLong(2));m.put("name",rs.getString(3));m.put("description",rs.getString(4));m.put("values",read(rs.getString(5)));m.put("createdAt",rs.getTimestamp(6).toInstant());return m;},plan);
    }
    public Map<String,Object> createPlan(PlanWrite dto,AdminPrincipal actor) {return writePlan(null,dto,actor);}
    public Map<String,Object> revisePlan(long id,PlanWrite dto,AdminPrincipal actor) {return writePlan(id,dto,actor);}
    private Map<String,Object> writePlan(Long id,PlanWrite dto,AdminPrincipal actor) {
        String name=bounded(dto.name(),100,"name"),description=dto.description()==null?"":dto.description().strip();
        if(description.length()>1000)throw new InvalidFieldException("description","Maximum 1000 characters.");
        Map<String,Object> values=FeatureRegistry.complete(dto.values());
        return tx.execute(s->{admins.requireAccountForUpdate(actor);long planId;long version;
            Map<String,Object> before=Map.of();
            if(id==null) {
                var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();
                db.update(c->{var ps=c.prepareStatement("insert into entitlement_plans(name,description,active,version) values(?,?,?,1)",new String[]{"id"});ps.setString(1,name);ps.setString(2,description);ps.setBoolean(3,dto.active());return ps;},holder);
                planId=Objects.requireNonNull(holder.getKey()).longValue();version=1;
            } else {
                var rows=db.queryForList("select id,version from entitlement_plans where id=? for update",id);
                if(rows.isEmpty())throw new InvalidFieldException("planId","Plan not found.");
                version=((Number)rows.getFirst().get("version")).longValue();
                if(version!=dto.expectedVersion())throw conflict();
                before=planRows(id).getFirst();
                version++;planId=id;db.update("update entitlement_plans set name=?,description=?,active=?,version=? where id=?",name,description,dto.active(),version,id);
            }
            db.update("insert into entitlement_plan_revisions(plan_id,revision,name,description,values_json) values(?,?,?,?,?)",planId,version,name,description,encode(values));
            Map<String,Object> result=planRows(planId).getFirst();
            event(null,planId,actor,id==null?"PLAN_CREATE":"PLAN_REVISE",null,before,result,"Plan configuration",null);return result;
        });
    }
    public Map<String,Object> assign(long tenant,AssignmentWrite dto,AdminPrincipal actor) {
        return tx.execute(s->{Restaurant r=lock(tenant);admins.requireAccountForUpdate(actor);policy(tenant);version(tenant,dto.expectedVersion());
            var revisions=db.queryForList("select r.id,p.active from entitlement_plan_revisions r join entitlement_plans p on p.id=r.plan_id where r.id=? for update",dto.revisionId());
            if(revisions.isEmpty()||!Boolean.TRUE.equals(revisions.getFirst().get("active")))throw new InvalidFieldException("revisionId","Choose a revision of an active plan.");
            Map<String,Object> before=effective(tenant,true);
            db.update("update restaurant_entitlement_policy set revision_id=?,version=version+1 where restaurant_id=?",dto.revisionId(),tenant);
            FeatureRegistry.dependencies(values(tenant,false));
            Map<String,Object> after=effective(tenant,true);event(tenant,null,actor,"PLAN_ASSIGN",null,before,after,bounded(dto.reason(),500,"reason"),null);cache.evict(r.getSlug());return after;
        });
    }
    public Map<String,Object> override(long tenant,String key,OverrideWrite dto,AdminPrincipal actor) {
        Object value=FeatureRegistry.validate(key,dto.value());String reason=bounded(dto.reason(),500,"reason");
        if(dto.expiresAt()!=null&&!dto.expiresAt().isAfter(Instant.now()))throw new InvalidFieldException("expiresAt","Expiry must be in the future.");
        return tx.execute(s->{Restaurant r=lock(tenant);admins.requireAccountForUpdate(actor);policy(tenant);version(tenant,dto.expectedVersion());
            Map<String,Object> before=effective(tenant,true);
            Map<String,Object> candidate=values(tenant,false);candidate.put(key,value);FeatureRegistry.dependencies(candidate);
            Instant starts=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if(dto.expiresAt()!=null&&!dto.expiresAt().isAfter(starts))throw new InvalidFieldException("expiresAt","Expiry must still be in the future.");
            db.update("delete from restaurant_entitlement_overrides where restaurant_id=? and feature_key=?",tenant,key);
            db.update("insert into restaurant_entitlement_overrides(restaurant_id,feature_key,value_json,reason,actor_id,starts_at,expires_at) values(?,?,?,?,?,?,?)",tenant,key,encode(value),reason,actor.adminId(),Timestamp.from(starts),stamp(dto.expiresAt()));
            FeatureRegistry.dependencies(values(tenant,false));db.update("update restaurant_entitlement_policy set version=version+1 where restaurant_id=?",tenant);
            Map<String,Object> after=effective(tenant,true);event(tenant,null,actor,"ENTITLEMENT_OVERRIDE",key,before.get("values"),after.get("values"),reason,dto.expiresAt());cache.evict(r.getSlug());return after;
        });
    }
    public Map<String,Object> removeOverride(long tenant,String key,long expected,String reason,AdminPrincipal actor) {
        FeatureRegistry.find(key);String note=bounded(reason,500,"reason");
        return tx.execute(s->{Restaurant r=lock(tenant);admins.requireAccountForUpdate(actor);policy(tenant);version(tenant,expected);Map<String,Object> before=effective(tenant,true);
            db.update("delete from restaurant_entitlement_overrides where restaurant_id=? and feature_key=?",tenant,key);FeatureRegistry.dependencies(values(tenant,false));
            db.update("update restaurant_entitlement_policy set version=version+1 where restaurant_id=?",tenant);Map<String,Object> after=effective(tenant,true);
            event(tenant,null,actor,"OVERRIDE_REMOVE",key,before.get("values"),after.get("values"),note,null);cache.evict(r.getSlug());return after;});
    }
    public List<?> allowedModes(long tenant) {
        return readSnapshot.execute(status->List.copyOf((List<?>)values(tenant).get("menu.allowedModes")));
    }
    public Map<String,Object> effective(long tenant,boolean adminView) {
        Integer isolation=TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        boolean coherent=Objects.equals(isolation,org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ)||Objects.equals(isolation,org.springframework.transaction.TransactionDefinition.ISOLATION_SERIALIZABLE);
        if(!TransactionSynchronizationManager.isActualTransactionActive() || (TransactionSynchronizationManager.isCurrentTransactionReadOnly()&&!coherent))
            return readSnapshot.execute(status->effectiveSnapshot(tenant,adminView));
        return effectiveSnapshot(tenant,adminView);
    }
    private Map<String,Object> effectiveSnapshot(long tenant,boolean adminView) {
        Instant at=Instant.now();
        Restaurant r=restaurants.findById(tenant).orElseThrow(()->new RestaurantNotFoundException(tenant));
        Map<String,Object> result=new LinkedHashMap<>();Map<String,Object> values=values(tenant,true,at);Map<String,String> origins=new LinkedHashMap<>();
        var assigned=db.queryForList("select p.version,r.id,r.revision,r.name from restaurant_entitlement_policy p left join entitlement_plan_revisions r on p.revision_id=r.id where p.restaurant_id=?",tenant);
        boolean legacy=assigned.isEmpty()||assigned.getFirst().get("id")==null;
        result.put("planName",legacy?"Legacy compatibility":assigned.getFirst().get("name"));result.put("revisionId",legacy?null:assigned.getFirst().get("id"));
        result.put("configVersion",assigned.isEmpty()?0:assigned.getFirst().get("version"));result.put("revision",legacy?null:assigned.getFirst().get("revision"));
        FeatureRegistry.defaults().keySet().forEach(k->origins.put(k,legacy?"LEGACY":"PLAN"));
        var overrides=overrides(tenant);Instant now=at;Instant next=null;
        for(var o:overrides){Instant expiry=(Instant)o.get("expiresAt");if(!((Instant)o.get("startsAt")).isAfter(now)&&(expiry==null||expiry.isAfter(now))){origins.put((String)o.get("key"),"OVERRIDE");if(expiry!=null&&(next==null||expiry.isBefore(next)))next=expiry;}}
        Map<String,Object> operational=new LinkedHashMap<>(),reasons=new LinkedHashMap<>();
        for(var f:FeatureRegistry.Feature.values())if(f.kind==FeatureRegistry.Kind.BOOLEAN){boolean allowed=Boolean.TRUE.equals(values.get(f.key));operational.put(f.key,f.implemented&&allowed);if(!f.implemented)reasons.put(f.key,"Planned — entitlement configurable; product not yet implemented");else if(!allowed)reasons.put(f.key,"Disabled by restaurant policy");}
        result.put("hardLimits",Map.of("ownerModelUploadBytes",props.maxModelBytes(),"publishedModelUploadBytes",props.maxPublishedModelBytes()));
        result.put("definitions",registry());
        result.put("values",values);result.put("origins",origins);result.put("operational",operational);result.put("reasons",reasons);result.put("nextReevaluationAt",next);
        Map<String,Object> usage=usage(tenant);result.put("usage",usage);List<String> over=new ArrayList<>();
        for(String key:List.of("dishes","models","conciergePerPeriod","tables"))if(exceeds(values.get("quota."+key),((Number)usage.get(key)).longValue()))over.add("quota."+key);
        result.put("overLimit",over);if(adminView){result.put("overrides",overrides);result.put("history",history(tenant));}
        return result;
    }
    private Map<String,Object> values(long tenant) { return values(tenant,true); }
    private Map<String,Object> values(long tenant,boolean mask) { return values(tenant,mask,Instant.now()); }
    private Map<String,Object> values(long tenant,boolean mask,Instant at) {
        Restaurant restaurant=restaurants.findById(tenant).orElseThrow(()->new RestaurantNotFoundException(tenant));
        Map<String,Object> values=FeatureRegistry.defaults();values.put("model.ownerUpload",Boolean.TRUE.equals(restaurant.getCanUpload3d()));
        var rows=db.query("select r.values_json from restaurant_entitlement_policy p join entitlement_plan_revisions r on p.revision_id=r.id where p.restaurant_id=?",(rs,n)->rs.getString(1),tenant);
        if(!rows.isEmpty())values=FeatureRegistry.complete(read(rows.getFirst()));
        Instant now=at;for(var override:overrides(tenant)){Instant expiry=(Instant)override.get("expiresAt");if(!((Instant)override.get("startsAt")).isAfter(now)&&(expiry==null||expiry.isAfter(now)))values.put((String)override.get("key"),override.get("value"));}
        // Expiring one dependency must fail safely, never retain a temporary grant through a stale cache.
        var modes=(List<?>)values.get("menu.allowedModes");
        if(mask&&!modes.contains("THREE_D")){values.put("model.ownerUpload",false);values.put("model.concierge",false);}
        if(mask&&!Boolean.TRUE.equals(values.get("qr.venue")))values.put("qr.table",false);
        if(mask)values.put("quota.modelUploadBytes",Math.min(((Number)values.get("quota.modelUploadBytes")).longValue(),props.maxModelBytes()));
        return values;
    }
    private List<Map<String,Object>> overrides(long tenant) {
        return db.query("select feature_key,value_json,reason,expires_at,starts_at from restaurant_entitlement_overrides where restaurant_id=? order by feature_key",(rs,n)->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("key",rs.getString(1));m.put("value",decode(rs.getString(2)));m.put("reason",rs.getString(3));m.put("expiresAt",rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant());m.put("startsAt",rs.getTimestamp(5).toInstant());return m;},tenant);
    }
    public List<Map<String,Object>> history(long tenant) {
        return db.query("select id,actor_id,actor_email,action,feature_key,before_json,after_json,reason,expires_at,created_at from entitlement_audit where restaurant_id=? order by id desc limit 100",(rs,n)->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong(1));m.put("actorId",rs.getLong(2));m.put("actor",rs.getString(3));m.put("action",rs.getString(4));m.put("key",rs.getString(5));m.put("before",decode(rs.getString(6)));m.put("after",decode(rs.getString(7)));m.put("reason",rs.getString(8));m.put("expiresAt",rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant());m.put("createdAt",rs.getTimestamp(10).toInstant());return m;},tenant);
    }
    private Map<String,Object> usage(long tenant) {
        Map<String,Object> u=new LinkedHashMap<>();u.put("dishes",count("select count(*) from media_details where restaurant_id=? and upload_status<>'FAILED'",tenant));
        u.put("models",count("select count(*) from media_details where restaurant_id=? and (model_s3_key is not null or content_type like 'model/%' or pending_model_s3_key is not null or model_request_status in ('PENDING','IN_PRODUCTION'))",tenant));
        u.put("retainedModelVersions",count("select count(*) from model_versions where restaurant_id=? and state='SUPERSEDED' and retention='HELD'",tenant));u.put("retainedModelBytes",count("select coalesce(sum(size_bytes),0) from model_versions where restaurant_id=? and state='SUPERSEDED' and retention='HELD'",tenant));
        u.put("conciergePerPeriod",monthly(tenant));u.put("period","UTC_CALENDAR_MONTH");u.put("tables",count("select count(*) from restaurant_tables where restaurant_id=? and active=true",tenant));return u;
    }
    public void requireDishCapacity(long tenant,boolean confirming) {
        requireTransaction();Map<String,Object> v=values(tenant);long used=((Number)usage(tenant).get("dishes")).longValue();
        if(exceeds(v.get("quota.dishes"),used+(confirming?0:1)))deny("Dish quota reached. Remove unused pending dishes or ask your administrator to adjust the limit.");
    }
    public void requireModelCapacity(long tenant,long dishId,long bytes,boolean owner) {
        requireTransaction();Map<String,Object> v=values(tenant);if(owner&&!Boolean.TRUE.equals(v.get("model.ownerUpload")))deny("Restaurant model uploads are disabled by your plan or override.");
        if(!((List<?>)v.get("menu.allowedModes")).contains("THREE_D"))deny("3D viewing is disabled by restaurant policy.");
        long limit=((Number)v.get("quota.modelUploadBytes")).longValue();if(bytes>Math.min(limit,props.maxModelBytes()))deny("Model upload exceeds the effective file-size limit.");
        var row=db.queryForList("select model_s3_key,content_type from media_details where restaurant_id=? and id=?",tenant,dishId);if(row.isEmpty())throw new MediaNotFoundException(dishId);
        if(row.getFirst().get("model_s3_key")!=null||String.valueOf(row.getFirst().get("content_type")).startsWith("model/"))return;
        long others=count("select count(*) from media_details where restaurant_id=? and id<>? and (model_s3_key is not null or content_type like 'model/%' or pending_model_s3_key is not null or model_request_status in ('PENDING','IN_PRODUCTION'))",tenant,dishId);
        if(exceeds(v.get("quota.models"),others+1))deny("Active model slot quota reached.");
    }
    public void requireTableCapacity(long tenant) {
        requireTransaction();requireFeature(tenant,"qr.table");Map<String,Object> v=values(tenant);
        long active=count("select count(*) from restaurant_tables where restaurant_id=? and active=true",tenant);
        if(exceeds(v.get("quota.tables"),active+1))deny("Active table quota reached. Existing tables remain available; deactivate a table or ask your administrator to adjust the limit.");
    }
    public void requireConciergeCapacity(long tenant) {
        requireTransaction();Map<String,Object> v=values(tenant);if(!Boolean.TRUE.equals(v.get("model.concierge")))deny("3D concierge is disabled by your plan or override.");
        if(exceeds(v.get("quota.conciergePerPeriod"),monthly(tenant)+1))deny("Concierge submission quota reached for this UTC calendar month.");
    }
    public void recordConcierge(long tenant,long dish) {
        requireTransaction();db.update("insert into entitlement_request_usage(operation_id,restaurant_id,dish_id,submitted_at) values(?,?,?,?)",UUID.randomUUID().toString(),tenant,dish,Timestamp.from(Instant.now()));
    }
    public void requireFeature(long tenant,String key) {
        if(!FeatureRegistry.find(key).implemented||!Boolean.TRUE.equals(values(tenant).get(key)))deny("This capability is not available for your restaurant.");
    }
    private long monthly(long tenant) {
        Instant start=LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end=LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return count("select count(*) from entitlement_request_usage where restaurant_id=? and submitted_at>=? and submitted_at<?",tenant,Timestamp.from(start),Timestamp.from(end));
    }
    private void event(Long tenant,Long plan,AdminPrincipal actor,String action,String key,Object before,Object after,String reason,Instant expiry) {
        if(before instanceof Map<?,?> m && m.containsKey("values")) before=snapshot(m);
        if(after instanceof Map<?,?> m && m.containsKey("values")) after=snapshot(m);
        audit.save(AdminAuditLog.builder().adminEmail(actor.email()).action(action).restaurantId(tenant).detail("Entitlement configuration change"+(key==null?"":": "+key)).build());
        db.update("insert into entitlement_audit(restaurant_id,plan_id,actor_id,actor_email,action,feature_key,before_json,after_json,reason,expires_at) values(?,?,?,?,?,?,?,?,?,?)",tenant,plan,actor.adminId(),actor.email(),action,key,encode(before),encode(after),reason,stamp(expiry));
    }
    private Map<String,Object> snapshot(Map<?,?> source) {
        Map<String,Object> result=new LinkedHashMap<>();for(String key:source.containsKey("latestRevisionId")?List.of("id","name","description","active","version","latestRevisionId","values"):List.of("planName","revisionId","configVersion","values"))result.put(key,source.get(key));return result;
    }
    public void setOwnerUploadGrant(long tenant,boolean enabled,AdminPrincipal actor) {
        tx.executeWithoutResult(status->{lock(tenant);policy(tenant);long current=count("select version from restaurant_entitlement_policy where restaurant_id=?",tenant);
            override(tenant,"model.ownerUpload",new OverrideWrite(enabled,current,"Updated through existing model permission control",null),actor);});
    }
    public void provision(long tenant,Long revision,Map<String,Object> overrides,AdminPrincipal actor) {
        if(revision!=null)assign(tenant,new AssignmentWrite(revision,0,"Initial restaurant plan"),actor);
        if(overrides==null||overrides.isEmpty())return;
        if(overrides.size()>FeatureRegistry.Feature.values().length)throw new InvalidFieldException("initialOverrides","Too many overrides.");
        lock(tenant);admins.requireAccountForUpdate(actor);policy(tenant);
        Map<String,Object> candidate=values(tenant,false);
        overrides.forEach((key,value)->candidate.put(key,FeatureRegistry.validate(key,value)));FeatureRegistry.dependencies(candidate);
        Map<String,Object> initial=values(tenant);
        for(var entry:overrides.entrySet()) {
            Object value=FeatureRegistry.validate(entry.getKey(),entry.getValue());
            db.update("insert into restaurant_entitlement_overrides(restaurant_id,feature_key,value_json,reason,actor_id,starts_at) values(?,?,?,?,?,?)",tenant,entry.getKey(),encode(value),"Initial provisioning exception",actor.adminId(),Timestamp.from(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
            db.update("update restaurant_entitlement_policy set version=version+1 where restaurant_id=?",tenant);
        }
        FeatureRegistry.dependencies(values(tenant,false));
        Map<String,Object> published=values(tenant);
        for(String key:overrides.keySet())event(tenant,null,actor,"ENTITLEMENT_OVERRIDE",key,initial,published,"Initial provisioning exception",null);
    }
    private Restaurant lock(long tenant) {return restaurants.findByIdForUpdate(tenant).orElseThrow(()->new RestaurantNotFoundException(tenant));}
    private void policy(long tenant){db.update("insert into restaurant_entitlement_policy(restaurant_id,version) values(?,0) on conflict do nothing",tenant);}
    private void version(long tenant,long expected){long actual=count("select version from restaurant_entitlement_policy where restaurant_id=?",tenant);if(actual!=expected)throw conflict();}
    private long count(String sql,Object...args){return Objects.requireNonNull(db.queryForObject(sql,Long.class,args));}
    private boolean exceeds(Object cap,long amount){return !"UNLIMITED".equals(cap)&&amount>((Number)cap).longValue();}
    private static void deny(String message){throw new org.springframework.security.access.AccessDeniedException(message);}
    private static void requireTransaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Quota check requires locked tenant transaction");}
    private static ConflictException conflict(){return new ConflictException("expectedVersion","Configuration changed; reload and review before saving.");}
    private static String bounded(String value,int max,String key){if(value==null||value.isBlank()||value.strip().length()>max)throw new InvalidFieldException(key,"Required; maximum "+max+" characters.");return value.strip();}
    private static Timestamp stamp(Instant value){return value==null?null:Timestamp.from(value.truncatedTo(java.time.temporal.ChronoUnit.MICROS));}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Cannot encode validated policy",e);}}
    private Object decode(String value){try{return json.readValue(value,Object.class);}catch(Exception e){throw new IllegalStateException("Invalid stored policy",e);}}
    private Map<String,Object> read(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Invalid stored policy",e);}}
}
