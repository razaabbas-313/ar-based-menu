package com.atelier.media.service;

import com.atelier.media.exception.*;
import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.*;
import com.atelier.media.security.Tenant;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.URI;
import java.util.*;

/** Curated appearance documents and link-only reviews; never executes owner-authored code. */
@Service
public class ProductStudioService {
    private final EntitlementRepository db;
    private final RestaurantRepository restaurants;
    private final RestaurantAccess access;
    private final EntitlementService entitlements;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    @org.springframework.beans.factory.annotation.Autowired private S3Storage storage;
    private static final Map<String,Set<String>> ENUMS=Map.of(
        "preset",Set.of("LEGACY","LUXURY","MINIMAL","DARK","ORGANIC","CONTEMPORARY","CLASSIC"),
        "heading",Set.of("SERIF","SANS"),"layout",Set.of("cards","compact"),"category",Set.of("pills","underline"),
        "button",Set.of("rounded","square"),"motion",Set.of("reduced","subtle","standard"),"card",Set.of("soft","outlined"));
    private static final Set<String> COLORS=Set.of("accent","background","surface","text");
    public ProductStudioService(EntitlementRepository db,RestaurantRepository restaurants,RestaurantAccess access,EntitlementService entitlements,TransactionTemplate tx,ObjectMapper json){this.db=db;this.restaurants=restaurants;this.access=access;this.entitlements=entitlements;this.tx=tx;this.json=json;}
    public Map<String,Object> appearance(Tenant tenant){return tx.execute(s->appearanceRow(lock(tenant)));}
    private Map<String,Object> appearanceRow(Restaurant r){
        var rows=db.queryForList("select * from restaurant_appearance where restaurant_id=?",r.getId());var row=rows.isEmpty()?Map.<String,Object>of():rows.getFirst();
        var published=decode((String)row.get("published_json"),legacy(r));
        Map<String,Object> result=new LinkedHashMap<>();result.put("version",row.getOrDefault("version",0L));result.put("published",published);result.put("draft",decode((String)row.get("draft_json"),published));result.put("publishedAt",row.get("published_at"));result.put("legacy",legacy(r));result.put("assets",db.query("select id,object_key,purpose from brand_studio_assets where restaurant_id=? and state='VERIFIED' order by created_at desc limit 100",(rs,n)->Map.of("id",rs.getString("id"),"url",storage.presignPrivateGet(rs.getString("object_key"),java.time.Duration.ofMinutes(15)),"purpose",rs.getString("purpose")),r.getId()));result.put("entitlements",entitlements.effective(r.getId(),false));return result;
    }
    public Map<String,Object> saveDraft(Tenant tenant,long expectedVersion,Map<String,String> values){
        var checked=validate(values);return tx.execute(s->{var r=lock(tenant);requireBrand(r,checked);validateAssets(r.getId(),checked);ensure(r.getId());version("restaurant_appearance",r.getId(),expectedVersion);db.update("update restaurant_appearance set draft_json=?,version=version+1 where restaurant_id=?",encode(checked),r.getId());return appearanceRow(r);});
    }
    public Map<String,Object> publish(Tenant tenant,long expectedVersion){return tx.execute(s->{var r=lock(tenant);ensure(r.getId());version("restaurant_appearance",r.getId(),expectedVersion);var draft=(Map<String,String>)appearanceRow(r).get("draft");requireBrand(r,validate(draft));validateAssets(r.getId(),draft);db.update("update restaurant_appearance set published_json=?,draft_json=?,published_at=CURRENT_TIMESTAMP,version=version+1 where restaurant_id=?",encode(draft),encode(draft),r.getId());return appearanceRow(r);});}
    public Map<String,Object> discard(Tenant tenant,long expectedVersion){return tx.execute(s->{var r=lock(tenant);ensure(r.getId());version("restaurant_appearance",r.getId(),expectedVersion);db.update("update restaurant_appearance set draft_json=published_json,version=version+1 where restaurant_id=?",r.getId());return appearanceRow(r);});}
    public Map<String,Object> reviewSettings(Tenant tenant){return tx.execute(s->{var r=lock(tenant);return review(r.getId());});}
    private Map<String,Object> review(long id){var rows=db.queryForList("select * from restaurant_reviews where restaurant_id=?",id);var row=rows.isEmpty()?Map.<String,Object>of():rows.getFirst();Map<String,Object> result=new LinkedHashMap<>();result.put("version",row.getOrDefault("version",0L));result.put("url",row.get("destination"));result.put("enabled",row.getOrDefault("enabled",false));result.put("allowed",feature(id,"reviews.enabled"));return result;}
    public Map<String,Object> saveReview(Tenant tenant,long expectedVersion,String url,boolean enabled){String destination=reviewUrl(url);if(enabled&&destination==null)throw invalid("url","Paste the Google Business Profile review link.");return tx.execute(s->{var r=lock(tenant);if(enabled)entitlements.requireFeature(r.getId(),"reviews.enabled");db.update("insert into restaurant_reviews(restaurant_id) values(?) on conflict do nothing",r.getId());version("restaurant_reviews",r.getId(),expectedVersion);db.update("update restaurant_reviews set destination=?,enabled=?,version=version+1 where restaurant_id=?",destination,enabled,r.getId());return review(r.getId());});}
    public Map<String,Object> publicAppearance(String slug){return tx.execute(s->{
        var r=restaurants.findBySlug(slug).filter(Restaurant::isPubliclyVisible).orElseThrow(()->new RestaurantNotFoundException(slug));
        var rows=db.queryForList("select published_json from restaurant_appearance where restaurant_id=?",String.class,r.getId());
        var published=decode(rows.isEmpty()?null:rows.getFirst(),legacy(r));
        var flags=(Map<?,?>)entitlements.effective(r.getId(),false).get("operational");
        if(!Boolean.TRUE.equals(flags.get("branding.enabled"))||(!withoutAssets(published).equals(legacy(r))&&!Boolean.TRUE.equals(flags.get("branding.premiumOptions"))))published=legacy(r);
        var reviews=db.queryForList("select destination from restaurant_reviews where restaurant_id=? and enabled=true and destination is not null",String.class,r.getId());
        boolean live=!Boolean.FALSE.equals(r.getIsMenuLive());
        Map<String,Object> publicReview=live&&Boolean.TRUE.equals(flags.get("reviews.enabled"))&&!reviews.isEmpty()?Map.of("url",reviews.getFirst()):Map.of();
        return Map.of("appearance",live?projectAssets(r.getId(),published):withoutAssets(published),"review",publicReview);
    });}
    public String publicAssetUrl(String id){
        if(id==null||!id.matches("[a-fA-F0-9-]{36}"))throw invalid("asset","Image unavailable.");
        return tx.execute(s->{var assets=db.queryForList("select restaurant_id,object_key from brand_studio_assets where id=? and state='VERIFIED'",id);if(assets.isEmpty())throw invalid("asset","Image unavailable.");var asset=assets.getFirst();var r=restaurants.findByIdForUpdate(((Number)asset.get("restaurant_id")).longValue()).filter(x->x.isPubliclyVisible()&&!Boolean.FALSE.equals(x.getIsMenuLive())).orElseThrow(()->invalid("asset","Image unavailable."));
            var appearance=(Map<?,?>)publicAppearance(r.getSlug()).get("appearance");String path="/api/v1/public/brand-assets/"+id;
            if(!path.equals(appearance.get("logoUrl"))&&!path.equals(appearance.get("bannerUrl")))throw invalid("asset","Image is not currently published.");
            return storage.presignPrivateGet((String)asset.get("object_key"),java.time.Duration.ofMinutes(15));});
    }
    public static String reviewUrl(String raw){
        if(raw==null||raw.isBlank())return null;
        if(raw.length()>600||!raw.equals(raw.strip())||raw.chars().anyMatch(c->c<=32||c==127))throw invalid("url","Use a supported HTTPS Google review link.");
        try{URI u=new URI(raw);if(!"https".equals(u.getScheme())||u.getRawUserInfo()!=null||u.getPort()!=-1||u.getRawFragment()!=null)throw new IllegalArgumentException();
            boolean shortLink="g.page".equals(u.getHost())&&u.getRawQuery()==null&&u.getRawPath().matches("/r/[A-Za-z0-9_-]{1,200}/review");
            boolean place="search.google.com".equals(u.getHost())&&"/local/writereview".equals(u.getRawPath())&&u.getRawQuery()!=null&&u.getRawQuery().matches("placeid=[A-Za-z0-9_-]{1,250}");
            if(!shortLink&&!place)throw new IllegalArgumentException();return u.toASCIIString();
        }catch(Exception e){throw invalid("url","Use https://g.page/r/…/review or https://search.google.com/local/writereview?placeid=… . Other destinations are unsupported.");}
    }
    public static Map<String,String> validate(Map<String,String> values){
        if(values==null||values.size()<ENUMS.size()+COLORS.size()||values.size()>ENUMS.size()+COLORS.size()+2||!values.keySet().containsAll(ENUMS.keySet())||!values.keySet().containsAll(COLORS))throw invalid("appearance","Choose all supported appearance options.");
        for(var entry:values.entrySet()){String key=entry.getKey(),value=entry.getValue();if(Set.of("logoAsset","bannerAsset").contains(key)){if(value==null||!value.matches("[a-fA-F0-9-]{36}"))throw invalid(key,"Choose an issued image from your restaurant.");}else if(COLORS.contains(key)){if(value==null||!value.matches("#[a-fA-F0-9]{6}"))throw invalid(key,"Use a six-digit hex color.");}else if(!ENUMS.containsKey(key)||!ENUMS.get(key).contains(value==null?"":value))throw invalid(key,"Choose a supported option.");}
        if(contrast(values.get("text"),values.get("background"))<4.5||contrast(values.get("text"),values.get("surface"))<4.5||contrast(values.get("accent"),values.get("background"))<4.5||contrast(values.get("accent"),values.get("surface"))<4.5)throw invalid("contrast","Choose text and accent contrast of at least 4.5:1 on both background and surfaces.");
        return Map.copyOf(values);
    }
    private static double luminance(String c){double sum=0;double[] weights={.2126,.7152,.0722};for(int i=0;i<3;i++){double v=Integer.parseInt(c.substring(1+i*2,3+i*2),16)/255.0;sum+=weights[i]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}return sum;}
    private static double contrast(String a,String b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
    private void requireBrand(Restaurant r,Map<String,String> values){entitlements.requireFeature(r.getId(),"branding.enabled");if(!feature(r.getId(),"branding.premiumOptions")&&!withoutAssets(values).equals(legacy(r)))throw new org.springframework.security.access.AccessDeniedException("Premium appearance options are unavailable. Use your existing restaurant theme.");}
    private static Map<String,String> withoutAssets(Map<String,String> values){var copy=new LinkedHashMap<>(values);copy.remove("logoAsset");copy.remove("bannerAsset");return copy;}
    private void validateAssets(long tenant,Map<String,String> values){for(String kind:List.of("logo","banner")){String id=values.get(kind+"Asset");if(id!=null&&db.queryForObject("select count(*) from brand_studio_assets where id=? and restaurant_id=? and purpose=? and state='VERIFIED'",Long.class,id,tenant,kind.toUpperCase(Locale.ROOT))!=1)throw invalid(kind+"Asset","Choose a verified image of the right kind from your restaurant.");}}
    private Map<String,String> projectAssets(long tenant,Map<String,String> values){Map<String,String> result=withoutAssets(values);for(String kind:List.of("logo","banner")){String id=values.get(kind+"Asset");if(id!=null){var urls=db.queryForList("select object_url from brand_studio_assets where id=? and restaurant_id=? and purpose=? and state='VERIFIED'",String.class,id,tenant,kind.toUpperCase(Locale.ROOT));if(!urls.isEmpty())result.put(kind+"Url",urls.getFirst());}}return result;}
    private boolean feature(long id,String key){return Boolean.TRUE.equals(((Map<?,?>)entitlements.effective(id,false).get("operational")).get(key));}
    private Restaurant lock(Tenant t){restaurants.findByIdForUpdate(t.restaurantId()).orElseThrow(()->new InvalidTokenException("Restaurant unavailable."));return access.requireStudioAccess(t);}
    private void ensure(long id){db.update("insert into restaurant_appearance(restaurant_id) values(?) on conflict do nothing",id);}
    private void version(String table,long id,long expected){long current=db.queryForObject("select version from "+table+" where restaurant_id=?",Long.class,id);if(current!=expected)throw new ConflictException("expectedVersion","Settings changed. Reload before saving.");}
    private Map<String,String> decode(String value,Map<String,String> fallback){try{return value==null?fallback:json.readValue(value,new TypeReference<Map<String,String>>(){});}catch(Exception e){throw new IllegalStateException("Appearance requires manual repair",e);}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Map<String,String> legacy(Restaurant r){String accent=switch(r.getThemeAccent()==null?"":r.getThemeAccent()){case "emerald"->"#34d399";case "zinc"->"#d4d4d8";case "gold"->"#e6c36a";default->"#fbbf24";};return Map.ofEntries(Map.entry("preset","LEGACY"),Map.entry("accent",accent),Map.entry("background","#0a0a0c"),Map.entry("surface","#18181b"),Map.entry("text","#f6efdf"),Map.entry("heading","SERIF"),Map.entry("layout","compact".equals(r.getMenuLayout())?"compact":"cards"),Map.entry("category","pills"),Map.entry("button","rounded"),Map.entry("motion","subtle"),Map.entry("card","soft"));}
    private static InvalidFieldException invalid(String field,String message){return new InvalidFieldException(field,message);}
}
