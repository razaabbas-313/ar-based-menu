package com.atelier.media.service;

import com.atelier.media.exception.InvalidFieldException;
import java.util.*;

/** Stable supported business keys, never executable admin-authored rules. */
public final class FeatureRegistry {
    public enum Kind { BOOLEAN, MODES, LIMIT, BYTES }
    public enum Feature {
        MODES("menu.allowedModes","Menu experiences",Kind.MODES,true,List.of("SIMPLE","THREE_D","AR"),0),
        OWNER_MODEL("model.ownerUpload","Restaurant model uploads",Kind.BOOLEAN,true,false,0),
        CONCIERGE("model.concierge","3D concierge",Kind.BOOLEAN,true,true,0),
        VENUE_QR("qr.venue","Restaurant QR",Kind.BOOLEAN,true,true,0),
        TABLE_QR("qr.table","Table QR",Kind.BOOLEAN,true,false,0),
        ORDERING("ordering.enabled","WhatsApp ordering",Kind.BOOLEAN,true,false,0),
        WAITER("waiter.enabled","WhatsApp waiter requests",Kind.BOOLEAN,true,false,0),
        REVIEWS("reviews.enabled","Google Reviews",Kind.BOOLEAN,true,false,0),
        BRANDING("branding.enabled","Brand customization",Kind.BOOLEAN,true,true,0),
        PREMIUM_BRAND("branding.premiumOptions","Premium appearance options",Kind.BOOLEAN,true,true,0),
        ANALYTICS("analytics.advanced","Advanced analytics",Kind.BOOLEAN,true,false,0),
        DISHES("quota.dishes","Dishes",Kind.LIMIT,true,"UNLIMITED",1_000_000),
        MODELS("quota.models","Active model slots",Kind.LIMIT,true,"UNLIMITED",1_000_000),
        TABLES("quota.tables","Restaurant tables",Kind.LIMIT,true,"UNLIMITED",10_000),
        REQUESTS("quota.conciergePerPeriod","Concierge submissions / UTC month",Kind.LIMIT,true,"UNLIMITED",1_000_000),
        MODEL_BYTES("quota.modelUploadBytes","Model upload bytes",Kind.BYTES,true,52_428_800L,52_428_800);
        public final String key,label; public final Kind kind; public final boolean implemented; public final Object defaultValue; public final long max;
        Feature(String key,String label,Kind kind,boolean implemented,Object value,long max) {
            this.key=key;this.label=label;this.kind=kind;this.implemented=implemented;this.defaultValue=value;this.max=max;
        }
    }
    private FeatureRegistry() {}
    public static Feature find(String key) {
        return Arrays.stream(Feature.values()).filter(f->f.key.equals(key)).findFirst().orElseThrow(()->invalid(key,"Unknown entitlement key."));
    }
    public static Map<String,Object> defaults() {
        Map<String,Object> values=new LinkedHashMap<>();for(Feature f:Feature.values())values.put(f.key,f.defaultValue);return values;
    }
    public static Object validate(String key,Object value) {
        Feature f=find(key);
        if(f.kind==Kind.BOOLEAN && value instanceof Boolean)return value;
        if(f.kind==Kind.MODES && value instanceof List<?> modes && !modes.isEmpty() && modes.size()<=3 && new HashSet<>(modes).size()==modes.size() &&
                modes.stream().allMatch(m->m instanceof String && Set.of("SIMPLE","THREE_D","AR").contains(m)) && modes.contains("SIMPLE"))return List.copyOf(modes);
        if(f.kind==Kind.LIMIT && "UNLIMITED".equals(value))return value;
        if((f.kind==Kind.LIMIT||f.kind==Kind.BYTES) && (value instanceof Integer||value instanceof Long) && ((Number)value).longValue()>=0 && ((Number)value).longValue()<=f.max)return ((Number)value).longValue();
        throw invalid(key,"Invalid "+f.kind+" value. Use the supported type and range (maximum "+f.max+").");
    }
    public static Map<String,Object> complete(Map<String,Object> supplied) {
        if(supplied==null||supplied.size()>Feature.values().length)throw invalid("values","Supply supported entitlement values.");
        Map<String,Object> values=defaults();supplied.forEach((k,v)->values.put(k,validate(k,v)));dependencies(values);return values;
    }
    public static void dependencies(Map<String,Object> values) {
        var modes=(List<?>)values.get("menu.allowedModes");
        if(modes.contains("AR")&&!modes.contains("THREE_D"))throw invalid("menu.allowedModes","AR requires 3D viewing.");
        if((Boolean.TRUE.equals(values.get("model.ownerUpload"))||Boolean.TRUE.equals(values.get("model.concierge")))&&!modes.contains("THREE_D"))throw invalid("menu.allowedModes","Model uploads and concierge require 3D viewing permission.");
        if(Boolean.TRUE.equals(values.get("qr.table"))&&!Boolean.TRUE.equals(values.get("qr.venue")))throw invalid("qr.table","Table QR requires restaurant QR permission.");
    }
    private static InvalidFieldException invalid(String key,String text){return new InvalidFieldException(key,text);}
}
