package com.atelier.media.service;

import com.atelier.media.exception.*;
import com.atelier.media.model.*;
import com.atelier.media.repository.*;
import com.atelier.media.security.Tenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

/** V2 received-UTC event counts. No visitor identity, revenue or delivery inference. */
@Service
public class GuestInsightService {
    public record Input(String slug,String eventId,String eventType,Long dishId,String tableToken,String category){}
    private static final Set<String> CLIENT=Set.of("MENU_VISIT","DISH_DETAIL_VIEW","CATEGORY_SELECTED","MODEL_VIEW_READY","AR_ATTEMPT","CART_ADD","IMAGE_GENERATED","SHARE_FLOW_OPENED","SHARE_REPORTED_COMPLETED","WAITER_CHAT_OPENED","REVIEW_LINK_OPENED");
    private static final Set<String> DISH=Set.of("DISH_DETAIL_VIEW","MODEL_VIEW_READY","AR_ATTEMPT","CART_ADD");
    private static final Set<String> ORDER=Set.of("CART_ADD","IMAGE_GENERATED","SHARE_FLOW_OPENED","SHARE_REPORTED_COMPLETED");
    private final EntitlementRepository db;private final RestaurantRepository restaurants;private final MediaRepository media;
    private final RestaurantAccess access;private final EntitlementService entitlements;private final TransactionTemplate tx;private final TransactionTemplate readTx;
    private GuestExperienceService guest;
    @org.springframework.beans.factory.annotation.Autowired public void setGuest(@org.springframework.context.annotation.Lazy GuestExperienceService guest){this.guest=guest;}
    public GuestInsightService(EntitlementRepository db,RestaurantRepository restaurants,MediaRepository media,RestaurantAccess access,EntitlementService entitlements,TransactionTemplate tx){this.db=db;this.restaurants=restaurants;this.media=media;this.access=access;this.entitlements=entitlements;this.tx=tx;this.readTx=new TransactionTemplate(tx.getTransactionManager());this.readTx.setReadOnly(true);this.readTx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);this.readTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
    public void record(Input dto,String ip){
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Client ingestion needs a detached transaction boundary");
        if(dto==null||!CLIENT.contains(dto.eventType()==null?"":dto.eventType()))throw invalid("eventType","Unsupported client observation.");
        if(dto.eventId()==null||!dto.eventId().matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw invalid("eventId","Use an event UUID.");
        if(dto.slug()==null||dto.slug().length()>160)throw invalid("slug","Choose a valid restaurant.");
        guest.checkRate("insights",ip,60);
        String slug=dto.slug().strip().toLowerCase(Locale.ROOT);
        var found=restaurants.findBySlug(slug).orElseThrow(()->new RestaurantNotFoundException(slug));
        tx.executeWithoutResult(s->{var r=restaurants.findByIdForUpdate(found.getId()).orElseThrow();
            if(!r.isPubliclyVisible()||Boolean.FALSE.equals(r.getIsMenuLive()))throw new RestaurantNotFoundException(dto.slug());
            var context=guest.publicContext(r.getSlug(),dto.tableToken());if(context.get("contextError")!=null)throw invalid("tableToken","Table context unavailable.");
            String type=dto.eventType(),mode=(String)context.get("menuMode");
            if(DISH.contains(type)&&dto.dishId()==null)throw invalid("dishId","Choose a dish.");
            if(dto.dishId()!=null){var dish=media.findByIdAndRestaurantId(dto.dishId(),r.getId()).orElseThrow(()->invalid("dishId","Dish unavailable."));if(dish.getUploadStatus()!=UploadStatus.COMPLETED||!Boolean.TRUE.equals(dish.getAvailable()))throw invalid("dishId","Dish unavailable.");if(("MODEL_VIEW_READY".equals(type)||"AR_ATTEMPT".equals(type))&&!dish.hasModel()&&!dish.isLegacyModelOnly())throw invalid("dishId","No model is available.");}
            if("MODEL_VIEW_READY".equals(type)&&"SIMPLE".equals(mode)||"AR_ATTEMPT".equals(type)&&!"AR".equals(mode))throw invalid("eventType","This interaction is unavailable in the current mode.");
            if(ORDER.contains(type)&&!Boolean.TRUE.equals(context.get("orderingAvailable")))throw invalid("eventType","Order requests unavailable.");
            if("WAITER_CHAT_OPENED".equals(type)&&!Boolean.TRUE.equals(context.get("waiterAvailable")))throw invalid("eventType","Waiter requests unavailable.");
            if("REVIEW_LINK_OPENED".equals(type)){entitlements.requireFeature(r.getId(),"reviews.enabled");if(db.queryForObject("select count(*) from restaurant_reviews where restaurant_id=? and enabled=true and destination is not null",Long.class,r.getId())==0)throw invalid("eventType","Reviews unavailable.");}
            String category=dto.category();if("CATEGORY_SELECTED".equals(type)){if(category==null||category.isBlank()||category.length()>80||category.matches("(?s).*\\p{Cntrl}.*"))throw invalid("category","Choose a bounded category key.");
                if(category.matches("[0-9]{1,18}")){if(db.queryForObject("select count(*) from categories where id=? and restaurant_id=? and is_active=true",Long.class,Long.parseLong(category),r.getId())!=1)throw invalid("category","Category unavailable.");}
                else if(!Set.of("STARTERS","MAINS","CHEF_SPECIALS","DESSERTS","DRINKS","OTHER").contains(category))throw invalid("category","Choose a known category key.");
            }else category=null;
            insert(r.getId(),dto.eventId().toLowerCase(Locale.ROOT),type,dto.dishId(),category,mode,context.get("tableLabel")!=null?"VALID_TABLE":"GENERAL");
        });
    }
    public void prepared(long restaurant,String reference,String type,String mode,boolean table){
        if(!Set.of("ORDER_PREPARED","WAITER_PREPARED").contains(type))throw new IllegalArgumentException();
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Preparation observation requires its transaction");
        insert(restaurant,reference,type,null,null,mode,table?"VALID_TABLE":"GENERAL");
    }
    private void insert(long restaurant,String id,String type,Long dish,String category,String mode,String context){Instant received=Instant.now();db.update("insert into guest_insight_events(restaurant_id,event_id,event_type,dish_id,category_key,menu_mode,entry_context,received_at,received_day) values(?,?,?,?,?,?,?,?,?) on conflict do nothing",restaurant,id,type,dish,category,mode,context,Timestamp.from(received),LocalDate.ofInstant(received,ZoneOffset.UTC));}
    public Map<String,Object> report(Tenant tenant,LocalDate from,LocalDate to){access.requireStudioAccess(tenant);return readTx.execute(s->{access.requireStudioAccess(tenant);boolean advanced=Boolean.TRUE.equals(((Map<?,?>)entitlements.effective(tenant.restaurantId(),false).get("operational")).get("analytics.advanced"));return reportFor(tenant.restaurantId(),from,to,advanced);});}
    public Map<String,Object> platform(LocalDate from,LocalDate to){return readTx.execute(s->reportFor(null,from,to,true));}
    private Map<String,Object> reportFor(Long tenant,LocalDate from,LocalDate to,boolean advanced){
        if(from==null||to==null||from.isAfter(to)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>89||to.isAfter(LocalDate.now(ZoneOffset.UTC))||from.isBefore(LocalDate.now(ZoneOffset.UTC).minusDays(89)))throw invalid("range","Choose an inclusive UTC range within the last 90 days.");
        String where=" where received_day>=? and received_day<=?"+(tenant==null?"":" and restaurant_id=?");Object[] args=tenant==null?new Object[]{from,to}:new Object[]{from,to,tenant};
        Map<String,Object> result=new LinkedHashMap<>();result.put("schemaVersion",2);result.put("from",from);result.put("to",to);result.put("timezone","UTC");result.put("advanced",advanced);result.put("totals",counts("event_type",where,args));
        result.put("trend",db.queryForList("select received_day as \"day\",event_type,count(*) as count from guest_insight_events"+where+" group by received_day,event_type order by received_day,event_type",args));
        result.put("context",advanced?counts("entry_context",where+" and event_type='MENU_VISIT'",args):Map.of());result.put("modes",advanced?counts("menu_mode",where+" and event_type='MENU_VISIT'",args):Map.of());
        result.put("topDishes",advanced?db.queryForList("select e.dish_id as dish_id,m.dish_title as name,count(*) as count from guest_insight_events e join media_details m on e.dish_id=m.id and e.restaurant_id=m.restaurant_id"+where.replace("restaurant_id=?","e.restaurant_id=?")+" and event_type='DISH_DETAIL_VIEW' group by e.dish_id,m.dish_title order by count(*) desc,e.dish_id limit 10",args):List.of());
        result.put("definitions",Map.of("cutover","V2 counts begin at deployment; legacy counters remain separate.","visits","Accepted MENU_VISIT observations; not unique people or physical QR scans.","models","Client-reported visible model-ready observations; AR counts are attempts, not placement.","requests","Server-validated preparations. Image/share steps are client observations, not delivery, acceptance or sales.","dedup","One accepted UUID per restaurant. Client authenticity is not guaranteed.","retention","Rolling received-UTC 90-day raw event window; no visitor/session IDs, notes, contacts or raw tokens."));return result;
    }
    private Map<String,Long> counts(String column,String where,Object[] args){Map<String,Long> counts=new LinkedHashMap<>();db.query("select "+column+",count(*) from guest_insight_events"+where+" group by "+column,rs->{counts.put(rs.getString(1),rs.getLong(2));},args);return counts;}
    /** Complete each bounded delete transaction, then continue until the expired backlog is drained. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="PT1H",initialDelayString="PT10M") public void expire(){
        while(Boolean.TRUE.equals(tx.execute(s->{var ids=db.queryForList("select id from guest_insight_events where received_day<? order by id limit 1000",Long.class,LocalDate.now(ZoneOffset.UTC).minusDays(89));for(Long id:ids)db.update("delete from guest_insight_events where id=?",id);return !ids.isEmpty();}))){if(Thread.currentThread().isInterrupted())return;}
        while(Boolean.TRUE.equals(tx.execute(s->{var keys=db.queryForList("select dedup_key from analytics_legacy_dedup where expires_at<CURRENT_TIMESTAMP limit 1000",String.class);for(String key:keys)db.update("delete from analytics_legacy_dedup where dedup_key=? and expires_at<CURRENT_TIMESTAMP",key);return !keys.isEmpty();}))){if(Thread.currentThread().isInterrupted())return;}
    }
    private static InvalidFieldException invalid(String field,String message){return new InvalidFieldException(field,message);}
}
