package com.atelier.media.service;

import com.atelier.media.exception.*;
import com.atelier.media.model.*;
import com.atelier.media.repository.*;
import com.atelier.media.security.Tenant;
import com.github.benmanes.caffeine.cache.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Guest context is not authentication or proof of presence. Prepared summaries are not orders accepted. */
@Service
public class GuestExperienceService {
    public record SettingsWrite(long expectedVersion,String menuMode,boolean orderingEnabled,boolean waiterEnabled,String orderNumber,String waiterNumber,String waiterMessage) {}
    public record TableWrite(String name,boolean active,long expectedVersion) {}
    public record OrderLine(Long dishId,@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=QuantityDeserializer.class) Integer quantity,BigDecimal expectedUnitPrice,String note) {}
    /** Reject Jackson's default fractional/string-to-integer coercion for this public input. */
    public static final class QuantityDeserializer extends com.fasterxml.jackson.databind.deser.std.StdDeserializer<Integer> {
        public QuantityDeserializer(){super(Integer.class);}
        @Override public Integer deserialize(com.fasterxml.jackson.core.JsonParser parser,com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            if(parser.currentToken()!=com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT)
                return context.reportInputMismatch(Integer.class,"Quantity must be a JSON integer.");
            return parser.getIntValue();
        }
    }
    public record OrderPrepare(List<OrderLine> items,String notes,String tableToken) {}
    public record WaiterWrite(String tableToken) {}
    private final EntitlementRepository db;
    private final RestaurantRepository restaurants;
    private final MediaRepository media;
    private final RestaurantAccess access;
    private final EntitlementService entitlements;
    private final TransactionTemplate tx;
    private final SecureRandom random=new SecureRandom();
    private final Cache<String,java.util.concurrent.atomic.AtomicInteger> attempts=Caffeine.newBuilder().maximumSize(20_000).expireAfterWrite(Duration.ofMinutes(1)).build();
    public GuestExperienceService(EntitlementRepository db,RestaurantRepository restaurants,MediaRepository media,RestaurantAccess access,EntitlementService entitlements,TransactionTemplate tx) {
        this.db=db;this.restaurants=restaurants;this.media=media;this.access=access;this.entitlements=entitlements;this.tx=tx;
    }
    public Map<String,Object> settings(Tenant tenant) {
        return tx.execute(s->{Restaurant r=lock(tenant);var config=read(r);var policy=entitlements.effective(r.getId(),false);
            var modes=(List<?>)((Map<?,?>)policy.get("values")).get("menu.allowedModes");
            config.put("storedMenuMode",config.get("menuMode"));config.put("legacyMode","LEGACY".equals(config.get("menuMode")));
            if("LEGACY".equals(config.get("menuMode")))config.put("menuMode",resolveMode("LEGACY",modes));
            config.put("effectiveMode",selectedMode(r.getId(),modes));config.put("allowedModes",modes);config.put("entitlements",policy);
            config.put("activeTables",tableCount(r.getId()));config.put("tableLimit",((Map<?,?>)policy.get("values")).get("quota.tables"));return config;});
    }
    public Map<String,Object> saveSettings(Tenant tenant,SettingsWrite dto) {
        if(!Set.of("SIMPLE","THREE_D","AR").contains(dto.menuMode()==null?"":dto.menuMode()))throw invalid("menuMode","Choose SIMPLE, THREE_D or AR.");
        String order=number(dto.orderNumber()),waiter=number(dto.waiterNumber());
        String message=clean(dto.waiterMessage()==null?"Please assist us.":dto.waiterMessage(),160,"waiterMessage",true);
        if(message.matches("(?is).*https?://.*")||message.toLowerCase(Locale.ROOT).contains("www."))throw invalid("waiterMessage","Use a short assistance message without links.");
        if(dto.orderingEnabled()&&order==null)throw invalid("orderNumber","An international WhatsApp number is required.");
        if(dto.waiterEnabled()&&waiter==null&&order==null)throw invalid("waiterNumber","Configure a waiter or order WhatsApp number.");
        return tx.execute(s->{Restaurant r=lock(tenant);ensure(r.getId());Map<String,Object> current=read(r);checkVersion(current,dto.expectedVersion());
            var allowed=entitlements.allowedModes(r.getId());
            if(!allowed.contains(dto.menuMode())&&!Objects.equals(current.get("menuMode"),dto.menuMode()))throw invalid("menuMode","This menu mode is not allowed by your policy.");
            if(dto.orderingEnabled())entitlements.requireFeature(r.getId(),"ordering.enabled");
            if(dto.waiterEnabled())entitlements.requireFeature(r.getId(),"waiter.enabled");
            db.update("update restaurant_guest_settings set menu_mode=?,ordering_enabled=?,waiter_enabled=?,order_number=?,waiter_number=?,waiter_message=?,version=version+1 where restaurant_id=?",
                dto.menuMode(),dto.orderingEnabled(),dto.waiterEnabled(),order,waiter,message,r.getId());return settings(tenant);});
    }
    public String selectedMode(long tenant,List<?> allowed) {
        String stored=db.query("select menu_mode from restaurant_guest_settings where restaurant_id=?",(rs,n)->rs.getString(1),tenant).stream().findFirst().orElse("LEGACY");
        return resolveMode(stored,allowed);
    }
    private static String resolveMode(String stored,List<?> allowed) {
        if("LEGACY".equals(stored))return allowed.contains("AR")?"AR":allowed.contains("THREE_D")?"THREE_D":"SIMPLE";
        return allowed.contains(stored)?stored:"SIMPLE";
    }
    public List<Map<String,Object>> tables(Tenant tenant) { return tables(tenant,0); }
    public List<Map<String,Object>> tables(Tenant tenant,long after) {
        return tx.execute(s->{Restaurant r=lock(tenant);boolean canQr=operational(r.getId(),"qr.table");
            return db.query("select * from restaurant_tables where restaurant_id=? and id>? order by id limit 200",(rs,n)->{
                Map<String,Object> row=new LinkedHashMap<>();row.put("id",rs.getLong("id"));row.put("name",rs.getString("name"));row.put("active",rs.getBoolean("active"));row.put("version",rs.getLong("version"));row.put("qrToken",canQr&&rs.getBoolean("active")?rs.getString("qr_token"):null);return row;},r.getId(),Math.max(0,after));});
    }
    public Map<String,Object> createTable(Tenant tenant,String name) {
        String label=clean(name,60,"name",true);
        return tx.execute(s->{Restaurant r=lock(tenant);entitlements.requireFeature(r.getId(),"qr.table");entitlements.requireTableCapacity(r.getId());
            uniqueName(r.getId(),label,null);String token=token();var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();
            db.update(c->{var ps=c.prepareStatement("insert into restaurant_tables(restaurant_id,name,normalized_name,qr_token,token_hash) values(?,?,?,?,?)",new String[]{"id"});ps.setLong(1,r.getId());ps.setString(2,label);ps.setString(3,label.toLowerCase(Locale.ROOT));ps.setString(4,token);ps.setString(5,hash(token));return ps;},holder);
            return table(tenant,Objects.requireNonNull(holder.getKey()).longValue());});
    }
    public Map<String,Object> updateTable(Tenant tenant,long id,TableWrite dto) {
        String label=clean(dto.name(),60,"name",true);
        return tx.execute(s->{Restaurant r=lock(tenant);var old=owned(r.getId(),id);checkVersion(old,dto.expectedVersion());
            if(dto.active()&&!Boolean.TRUE.equals(old.get("active"))){entitlements.requireFeature(r.getId(),"qr.table");entitlements.requireTableCapacity(r.getId());}
            uniqueName(r.getId(),label,id);db.update("update restaurant_tables set name=?,normalized_name=?,active=?,version=version+1,updated_at=CURRENT_TIMESTAMP where id=? and restaurant_id=?",label,label.toLowerCase(Locale.ROOT),dto.active(),id,r.getId());return table(tenant,id);});
    }
    public Map<String,Object> rotateTable(Tenant tenant,long id,long expectedVersion) {
        return tx.execute(s->{Restaurant r=lock(tenant);entitlements.requireFeature(r.getId(),"qr.table");var old=owned(r.getId(),id);checkVersion(old,expectedVersion);String token=token();
            db.update("update restaurant_tables set qr_token=?,token_hash=?,version=version+1,updated_at=CURRENT_TIMESTAMP where id=? and restaurant_id=?",token,hash(token),id,r.getId());return table(tenant,id);});
    }
    private Map<String,Object> table(Tenant tenant,long id){var raw=owned(tenant.restaurantId(),id);Map<String,Object> result=new LinkedHashMap<>();for(String key:List.of("id","name","active","version"))result.put(key,raw.get(key));result.put("qrToken",operational(tenant.restaurantId(),"qr.table")&&Boolean.TRUE.equals(raw.get("active"))?raw.get("qr_token"):null);return result;}
    public Map<String,Object> publicContext(String slug,String tableToken) {
        Restaurant found=publicRestaurant(slug);
        return tx.execute(s->{Restaurant r=publicLocked(found.getId());var config=read(r);var policy=entitlements.effective(r.getId(),false);var flags=(Map<?,?>)policy.get("operational");var modes=(List<?>)((Map<?,?>)policy.get("values")).get("menu.allowedModes");
            Map<String,Object> result=new LinkedHashMap<>();String label=null,error=null;
            try{label=resolveTable(r.getId(),tableToken);}catch(InvalidFieldException|org.springframework.security.access.AccessDeniedException e){error="This table QR is invalid, disabled or no longer available. Open the restaurant menu without the table link.";}
            boolean live=!Boolean.FALSE.equals(r.getIsMenuLive());String order=(String)config.get("orderNumber"),waiter=recipient(config,true);
            result.put("menuMode",selectedMode(r.getId(),modes));result.put("tableLabel",label);result.put("contextError",error);
            result.put("orderingAvailable",live&&error==null&&Boolean.TRUE.equals(flags.get("ordering.enabled"))&&Boolean.TRUE.equals(config.get("orderingEnabled"))&&order!=null);
            result.put("waiterAvailable",live&&error==null&&Boolean.TRUE.equals(flags.get("waiter.enabled"))&&Boolean.TRUE.equals(config.get("waiterEnabled"))&&waiter!=null);
            return result;});
    }
    /** No order database or notes retention: immutable verified preparation data is returned once. */
    public Map<String,Object> prepareOrder(String slug,OrderPrepare dto,String ip) {
        checkRate("prepare",ip,10);Restaurant found=publicRestaurant(slug);
        if(dto.items()==null||dto.items().isEmpty()||dto.items().size()>50)throw invalid("items","Choose between 1 and 50 distinct dishes.");
        String notes=clean(dto.notes(),500,"notes",false);
        TransactionTemplate snapshotTx=new TransactionTemplate(tx.getTransactionManager());
        snapshotTx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshotTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return snapshotTx.execute(s->{Restaurant r=publicLocked(found.getId());requireLive(r);entitlements.requireFeature(r.getId(),"ordering.enabled");var config=read(r);
            if(!Boolean.TRUE.equals(config.get("orderingEnabled")))deny("Ordering is disabled by this restaurant.");String recipient=recipient(config,false);if(recipient==null)deny("No usable restaurant WhatsApp number is configured.");
            String label=resolveTable(r.getId(),dto.tableToken());Set<Long> seen=new HashSet<>();List<Map<String,Object>> lines=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;
            for(OrderLine line:dto.items()) {
                if(line==null||line.dishId()==null||!seen.add(line.dishId())||line.quantity()==null||line.quantity()<1||line.quantity()>99)throw invalid("items","Use distinct dishes with quantities from 1 to 99.");
                String note=clean(line.note(),200,"itemNote",false);Media dish=media.findByIdAndRestaurantId(line.dishId(),r.getId()).orElseThrow(()->invalid("items","A dish is no longer available in this restaurant."));
                if(dish.getUploadStatus()!=UploadStatus.COMPLETED||!Boolean.TRUE.equals(dish.getAvailable())||dish.getPriceTag()==null||dish.getPriceTag().signum()<0)throw invalid("items","A dish is unavailable or cannot be priced. Refresh your cart.");
                BigDecimal price=dish.getPriceTag().setScale(2,RoundingMode.UNNECESSARY);
                if(line.expectedUnitPrice()!=null&&line.expectedUnitPrice().compareTo(price)!=0)throw new ConflictException("items","A price changed. Refresh the menu and review your cart before preparing again.");
                BigDecimal amount=price.multiply(BigDecimal.valueOf(line.quantity()));total=total.add(amount);
                Map<String,Object> verified=new LinkedHashMap<>();verified.put("dishId",dish.getId());verified.put("name",dish.getDishTitle());verified.put("quantity",line.quantity());verified.put("unitPrice",price.toPlainString());verified.put("lineTotal",amount.toPlainString());verified.put("note",note);lines.add(Collections.unmodifiableMap(verified));
            }
            Map<String,Object> snapshot=new LinkedHashMap<>();Instant now=Instant.now();snapshot.put("reference",UUID.randomUUID().toString());snapshot.put("status","PREPARED_REQUEST");snapshot.put("restaurantName",r.getName());snapshot.put("currency",r.getCurrencyCode());snapshot.put("currencySymbol",r.getCurrencySymbol());snapshot.put("themeAccent",r.getThemeAccent());snapshot.put("tableLabel",label);snapshot.put("lines",List.copyOf(lines));snapshot.put("estimatedTotal",total.setScale(2).toPlainString());snapshot.put("notes",notes);snapshot.put("recipient",recipient);snapshot.put("preparedAt",now);snapshot.put("expiresAt",now.plus(Duration.ofMinutes(10)));snapshot.put("disclaimer","Order Request — Not Yet Accepted");return Collections.unmodifiableMap(snapshot);
        });
    }
    public Map<String,Object> waiter(String slug,WaiterWrite dto,String ip) {
        checkRate("waiter",ip,3);Restaurant found=publicRestaurant(slug);
        return tx.execute(s->{Restaurant r=publicLocked(found.getId());requireLive(r);entitlements.requireFeature(r.getId(),"waiter.enabled");var config=read(r);
            if(!Boolean.TRUE.equals(config.get("waiterEnabled")))deny("Waiter requests are disabled by this restaurant.");String recipient=recipient(config,true);if(recipient==null)deny("No usable waiter WhatsApp number is configured.");
            String label=resolveTable(r.getId(),dto.tableToken());String message=config.get("waiterMessage")+"\nRestaurant: "+r.getName()+"\n"+(label==null?"General assistance requested.":"Table: "+label)+"\nPlease send this message in WhatsApp.";
            Map<String,Object> result=new LinkedHashMap<>();result.put("recipient",recipient);result.put("tableLabel",label);result.put("message",message);result.put("url","https://wa.me/"+recipient+"?text="+java.net.URLEncoder.encode(message,StandardCharsets.UTF_8));result.put("status","CHAT_READY_NOT_SENT");return result;});
    }
    public void checkRate(String operation,String ip,int max) {
        String key=operation+":"+hash(ip==null?"unknown":ip);int count=attempts.get(key,k->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        if(count>max)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"Please wait a minute before trying again.");
    }
    private String resolveTable(long tenant,String token) {
        if(token==null||token.isBlank())return null;
        if(!token.matches("[A-Za-z0-9_-]{43}"))throw invalid("tableToken","Invalid table QR context.");entitlements.requireFeature(tenant,"qr.table");
        return db.query("select name from restaurant_tables where restaurant_id=? and token_hash=? and active=true",(rs,n)->rs.getString(1),tenant,hash(token)).stream().findFirst().orElseThrow(()->invalid("tableToken","Invalid table QR context."));
    }
    private Map<String,Object> read(Restaurant r) {
        var rows=db.queryForList("select * from restaurant_guest_settings where restaurant_id=?",r.getId());Map<String,Object> row=rows.isEmpty()?Map.of():rows.getFirst();long version=((Number)row.getOrDefault("version",0L)).longValue();
        Map<String,Object> config=new LinkedHashMap<>();config.put("version",version);config.put("menuMode",row.getOrDefault("menu_mode","LEGACY"));config.put("orderingEnabled",row.getOrDefault("ordering_enabled",false));config.put("waiterEnabled",row.getOrDefault("waiter_enabled",false));
        String order=(String)row.get("order_number");if(version==0&&order==null)try{order=number(r.getWhatsappNumber());}catch(InvalidFieldException ignored){order=null;}
        config.put("orderNumber",order);config.put("waiterNumber",row.get("waiter_number"));config.put("waiterMessage",row.getOrDefault("waiter_message","Please assist us."));return config;
    }
    public static String number(String raw) {
        if(raw==null||raw.isBlank())return null;String normalized=raw.strip().replaceAll("[ ()-]","");
        if(!normalized.matches("\\+?[1-9][0-9]{9,14}"))throw invalid("number","Use a full international number, 10–15 digits, with an optional leading +.");return normalized.replaceFirst("^\\+","");
    }
    private String recipient(Map<String,Object> config,boolean waiter){String value=(String)(waiter?config.get("waiterNumber"):config.get("orderNumber"));if(waiter&&(value==null||value.isBlank()))value=(String)config.get("orderNumber");return number(value);}
    private Restaurant lock(Tenant tenant){restaurants.findByIdForUpdate(tenant.restaurantId()).orElseThrow(()->new InvalidTokenException("Restaurant account is no longer available."));return access.requireStudioAccess(tenant);}
    private Restaurant publicRestaurant(String slug){return restaurants.findBySlug(slug).filter(Restaurant::isPubliclyVisible).orElseThrow(()->new RestaurantNotFoundException(slug));}
    private Restaurant publicLocked(long id){return restaurants.findByIdForUpdate(id).filter(Restaurant::isPubliclyVisible).orElseThrow(()->new RestaurantNotFoundException(id));}
    private void requireLive(Restaurant r){if(Boolean.FALSE.equals(r.getIsMenuLive()))deny("This menu is not publicly available for requests.");}
    private boolean operational(long tenant,String key){return Boolean.TRUE.equals(((Map<?,?>)entitlements.effective(tenant,false).get("operational")).get(key));}
    private void ensure(long id){db.update("insert into restaurant_guest_settings(restaurant_id) values(?) on conflict do nothing",id);}
    private long tableCount(long id){return Objects.requireNonNull(db.queryForObject("select count(*) from restaurant_tables where restaurant_id=? and active=true",Long.class,id));}
    private Map<String,Object> owned(long tenant,long id){var rows=db.queryForList("select * from restaurant_tables where restaurant_id=? and id=?",tenant,id);if(rows.isEmpty())throw invalid("table","Table not found in this restaurant.");return rows.getFirst();}
    private void uniqueName(long tenant,String label,Long exclude){long matches=Objects.requireNonNull(db.queryForObject("select count(*) from restaurant_tables where restaurant_id=? and normalized_name=? and id<>?",Long.class,tenant,label.toLowerCase(Locale.ROOT),exclude==null?-1:exclude));if(matches>0)throw new ConflictException("name","A table with this name already exists.");}
    private void checkVersion(Map<String,Object> row,long version){if(((Number)row.get("version")).longValue()!=version)throw new ConflictException("expectedVersion","Settings changed; reload before saving.");}
    private String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String clean(String value,int max,String field,boolean required){String s=value==null?"":value.strip();if((required&&s.isEmpty())||s.length()>max||(required?s:s.replace("\n","").replace("\r","").replace("\t","")).matches("(?s).*\\p{Cntrl}.*"))throw invalid(field,"Use "+(required?"1":"0")+"–"+max+" plain-text characters without control characters.");return s;}
    private static InvalidFieldException invalid(String field,String message){return new InvalidFieldException(field,message);}
    private static void deny(String message){throw new org.springframework.security.access.AccessDeniedException(message);}
}
