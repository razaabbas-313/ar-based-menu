package com.atelier.media.service;

import com.atelier.media.model.*;
import com.atelier.media.repository.*;
import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.security.Tenant;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** New metric dictionary, actual isolated H2 persistence, no session tracking or physical-scan claims. */
@SpringBootTest(properties={
 "spring.config.import=optional:classpath:guest-insight-no-secrets.properties",
 "spring.datasource.url=jdbc:h2:mem:sprint5_guest_insights;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.flyway.locations=classpath:db/migration/h2","atelier.scheduling.enabled=false",
 "aws.access-key-id=AKIAIOSFODNN7EXAMPLE","aws.secret-access-key=synthetic-test-secret-not-for-aws",
 "aws.region=us-east-1","aws.s3.bucket=atelier-test-bucket"
})
@ActiveProfiles("test")
class GuestInsightIntegrationTest {
 @Autowired GuestInsightService service;
 @Autowired GuestExperienceService guest;
 @Autowired EntitlementService entitlements;
 @org.springframework.boot.test.mock.mockito.SpyBean RestaurantRepository restaurants;
 @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
 @Autowired MediaRepository media;
 @Autowired PlatformUserRepository admins;
 @Autowired JdbcTemplate db;
 @Autowired org.springframework.transaction.support.TransactionTemplate tx;
 @MockBean S3Storage storage;
 Restaurant restaurant;Restaurant other;Media dish;PlatformUser admin;

 @BeforeEach void seed(){
  restaurant=restaurant("insights-");other=restaurant("other-insights-");
  dish=TestFixtures.mediaWithModel(1,restaurant.getId(),restaurant.getSlug());dish.setId(null);dish.setVersion(null);dish=media.saveAndFlush(dish);
  admin=admins.saveAndFlush(PlatformUser.builder().email(UUID.randomUUID()+"@platform.test").displayName("Synthetic insights operator").passwordHash("synthetic-test-placeholder").mustChangePassword(false).build());
 }
 Restaurant restaurant(String prefix){Restaurant r=TestFixtures.restaurant(1,prefix+UUID.randomUUID());r.setId(null);return restaurants.saveAndFlush(r);}
 Tenant tenant(){return new Tenant(restaurant.getId(),restaurant.getSlug(),false,restaurant.getTokenVersion());}
 LocalDate today(){return LocalDate.now(ZoneOffset.UTC);}
 String ip(){return "synthetic-insights-client-"+UUID.randomUUID();}
 GuestInsightService.Input input(String type,String id,Long dishId,String token){return new GuestInsightService.Input(restaurant.getSlug(),id,type,dishId,token,null);}
 void record(String type){service.record(input(type,UUID.randomUUID().toString(),null,null),ip());}
 long rows(){return db.queryForObject("select count(*) from guest_insight_events where restaurant_id=?",Long.class,restaurant.getId());}
 Map<String,Object> report(){return service.report(tenant(),today(),today());}
 long total(Map<String,Object> report,String type){Object value=((Map<?,?>)report.get("totals")).get(type);return value==null?0L:((Number)value).longValue();}
 void grant(String key,Object value){long version=((Number)entitlements.effective(restaurant.getId(),true).get("configVersion")).longValue();entitlements.override(restaurant.getId(),key,new EntitlementService.OverrideWrite(value,version,"Synthetic insights policy",null),new AdminPrincipal(admin.getId(),admin.getEmail(),admin.getTokenVersion()));}

 @Test void successfulRecordCommitsAndUuidRetryCountsExactlyOnce(){
  String id=UUID.randomUUID().toString();service.record(input("MENU_VISIT",id,null,null),ip());service.record(input("MENU_VISIT",id,null,null),ip());
  assertThat(rows()).isEqualTo(1);assertThat(total(report(),"MENU_VISIT")).isEqualTo(1);
 }
 @Test void concurrentDuplicateEventIdCountsOnlyOnce() throws Exception {
  String id=UUID.randomUUID().toString();int n=10;ExecutorService pool=Executors.newFixedThreadPool(n);CountDownLatch start=new CountDownLatch(1);
  try{List<Future<?>> futures=new ArrayList<>();for(int i=0;i<n;i++)futures.add(pool.submit(()->{start.await();service.record(input("MENU_VISIT",id,null,null),ip());return null;}));start.countDown();for(Future<?> f:futures)f.get(30,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  assertThat(rows()).isEqualTo(1);assertThat(total(report(),"MENU_VISIT")).isEqualTo(1);
 }
 @Test void distinctConcurrentEventsAllReachReportWithoutLostIncrements() throws Exception {
  int n=10;ExecutorService pool=Executors.newFixedThreadPool(n);CountDownLatch start=new CountDownLatch(1);
  try{List<Future<?>> futures=new ArrayList<>();for(int i=0;i<n;i++)futures.add(pool.submit(()->{start.await();record("MENU_VISIT");return null;}));start.countDown();for(Future<?> f:futures)f.get(30,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  assertThat(rows()).isEqualTo(n);assertThat(total(report(),"MENU_VISIT")).isEqualTo(n);
 }
 @Test void eventIdsAreScopedToRestaurant(){
  String id=UUID.randomUUID().toString();service.record(input("MENU_VISIT",id,null,null),ip());service.record(new GuestInsightService.Input(other.getSlug(),id,"MENU_VISIT",null,null,null),ip());
  assertThat(rows()).isEqualTo(1);assertThat(db.queryForObject("select count(*) from guest_insight_events where restaurant_id=?",Long.class,other.getId())).isEqualTo(1);
 }
 @Test void malformedIdsAndUnknownOrServerOnlyEventTypesAreRejected(){
  assertThatThrownBy(()->service.record(input("MENU_VISIT","not-a-uuid",null,null),ip())).isInstanceOf(RuntimeException.class);
  for(String type:List.of("PAYMENT_COMPLETED","ORDER_PREPARED","WAITER_PREPARED","DELIVERY_CONFIRMED","RESTAURANT_ACCEPTED"))assertThatThrownBy(()->record(type)).isInstanceOf(RuntimeException.class);
  assertThat(rows()).isZero();
 }
 @Test void tableContextIsResolvedAndRevocationStopsNewAttribution(){
  grant("qr.table",true);grant("analytics.advanced",true);var table=guest.createTable(tenant(),"Terrace 01");String token=(String)table.get("qrToken");
  service.record(input("MENU_VISIT",UUID.randomUUID().toString(),null,token),ip());record("MENU_VISIT");
  Map<String,Object> result=report();assertThat(((Map<?,?>)result.get("context")).get("VALID_TABLE")).isEqualTo(1L);assertThat(((Map<?,?>)result.get("context")).get("GENERAL")).isEqualTo(1L);
  guest.rotateTable(tenant(),((Number)table.get("id")).longValue(),((Number)table.get("version")).longValue());
  assertThatThrownBy(()->service.record(input("MENU_VISIT",UUID.randomUUID().toString(),null,token),ip())).isInstanceOf(RuntimeException.class);
  assertThat(rows()).isEqualTo(2);
 }
 @Test void foreignAndUnusableDishesAreRejected(){
  Media foreign=TestFixtures.media(1,other.getId(),other.getSlug(),UploadStatus.COMPLETED);foreign.setId(null);foreign.setVersion(null);foreign=media.saveAndFlush(foreign);long foreignId=foreign.getId();
  assertThatThrownBy(()->service.record(input("DISH_DETAIL_VIEW",UUID.randomUUID().toString(),foreignId,null),ip())).isInstanceOf(RuntimeException.class);
  dish.setAvailable(false);media.saveAndFlush(dish);assertThatThrownBy(()->service.record(input("DISH_DETAIL_VIEW",UUID.randomUUID().toString(),dish.getId(),null),ip())).isInstanceOf(RuntimeException.class);assertThat(rows()).isZero();
 }
 @Test void dishDetailReportUsesCurrentTenantOwnedNamesAndIndependentTaxonomy(){
  grant("analytics.advanced",true);service.record(input("DISH_DETAIL_VIEW",UUID.randomUUID().toString(),dish.getId(),null),ip());
  Map<String,Object> result=report();assertThat(total(result,"DISH_DETAIL_VIEW")).isEqualTo(1);assertThat(total(result,"MENU_VISIT")).isZero();
  assertThat((List<?>)result.get("topDishes")).hasSize(1);assertThat(result.get("definitions")).isNotNull();
  assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getMenuViewsCount()).as("V2 metrics do not reinterpret legacy cumulative menu counts").isZero();
 }
 @Test void activeSimpleModeRejectsModelAndArTelemetry(){
  guest.saveSettings(tenant(),new GuestExperienceService.SettingsWrite(0,"SIMPLE",false,false,null,null,"Please assist us."));
  for(String type:List.of("MODEL_VIEW_READY","AR_ATTEMPT"))assertThatThrownBy(()->service.record(input(type,UUID.randomUUID().toString(),dish.getId(),null),ip())).isInstanceOf(RuntimeException.class);assertThat(rows()).isZero();
 }
 @Test void disabledOrderWaiterAndReviewPermissionsRejectTheirTelemetry(){
  for(String type:List.of("CART_ADD","IMAGE_GENERATED","SHARE_FLOW_OPENED","SHARE_REPORTED_COMPLETED","WAITER_CHAT_OPENED","REVIEW_LINK_OPENED"))assertThatThrownBy(()->service.record(input(type,UUID.randomUUID().toString(),dish.getId(),null),ip())).isInstanceOf(RuntimeException.class);assertThat(rows()).isZero();
 }
 @Test void restrictedRestaurantsAndRevokedOwnerCannotUseIngestionOrReports(){
  restaurant.setStatus(RestaurantStatus.SUSPENDED);restaurants.saveAndFlush(restaurant);assertThatThrownBy(()->record("MENU_VISIT")).isInstanceOf(RuntimeException.class);assertThatThrownBy(this::report).isInstanceOf(RuntimeException.class);
  restaurant.setStatus(RestaurantStatus.ACTIVE);restaurant.incrementTokenVersion();restaurants.saveAndFlush(restaurant);Tenant revoked=new Tenant(restaurant.getId(),restaurant.getSlug(),false,restaurant.getTokenVersion()-1);assertThatThrownBy(()->service.report(revoked,today(),today())).isInstanceOf(RuntimeException.class);
 }
 @Test void basicMetricsRemainAvailableButPremiumBreakdownsAreMasked(){
  record("MENU_VISIT");var basic=report();assertThat(basic.get("advanced")).isEqualTo(false);assertThat(total(basic,"MENU_VISIT")).isEqualTo(1);assertThat((Map<?,?>)basic.get("context")).isEmpty();assertThat((Map<?,?>)basic.get("modes")).isEmpty();assertThat((List<?>)basic.get("topDishes")).isEmpty();
  grant("analytics.advanced",true);var premium=report();assertThat(premium.get("advanced")).isEqualTo(true);assertThat(((Map<?,?>)premium.get("context")).get("GENERAL")).isEqualTo(1L);assertThat(((Map<?,?>)premium.get("modes")).get("AR")).isEqualTo(1L);
 }
 @Test void reportRangeIsBoundedAndReceivedUtcDayIsReported(){
  record("MENU_VISIT");assertThat((List<?>)report().get("trend")).isNotEmpty();
  assertThatThrownBy(()->service.report(tenant(),today(),today().minusDays(1))).isInstanceOf(RuntimeException.class);assertThatThrownBy(()->service.report(tenant(),today().minusDays(91),today())).isInstanceOf(RuntimeException.class);
  assertThat(total(service.report(tenant(),today().minusDays(2),today().minusDays(1)),"MENU_VISIT")).isZero();
 }
 @Test void schemaStoresNoRawIpTableTokenNotesOrVisitorSessionId(){
  var columns=db.queryForList("select column_name from information_schema.columns where table_name='guest_insight_events'",String.class);
  assertThat(columns).doesNotContain("raw_ip","client_ip","table_token","notes","session_id","visitor_id");
 }

 @Test void retentionDrainsMoreThanOneBatchAndPreservesCurrentBoundaryAndFreshDedup(){
  record("MENU_VISIT");LocalDate received=today();java.time.Instant now=java.time.Instant.now();
  String prefix=UUID.randomUUID().toString().replace("-",""),fresh=UUID.randomUUID().toString().replace("-","")+"f".repeat(32);
  tx.executeWithoutResult(status->{
   List<Object[]> expired=new ArrayList<>(),dedup=new ArrayList<>();
   for(int i=0;i<1005;i++){
    expired.add(new Object[]{restaurant.getId(),UUID.randomUUID().toString(),"MENU_VISIT","AR","GENERAL",received.minusDays(90),java.sql.Timestamp.from(now.minus(java.time.Duration.ofDays(90)))});
    dedup.add(new Object[]{prefix+String.format("%032x",i),java.sql.Timestamp.from(now.minusSeconds(60))});
   }
   db.batchUpdate("insert into guest_insight_events(restaurant_id,event_id,event_type,menu_mode,entry_context,received_day,received_at) values(?,?,?,?,?,?,?)",expired);
   db.update("insert into guest_insight_events(restaurant_id,event_id,event_type,menu_mode,entry_context,received_day,received_at) values(?,?,?,?,?,?,?)",restaurant.getId(),UUID.randomUUID().toString(),"MENU_VISIT","AR","GENERAL",received.minusDays(89),java.sql.Timestamp.from(now.minus(java.time.Duration.ofDays(89))));
   db.batchUpdate("insert into analytics_legacy_dedup(dedup_key,expires_at) values(?,?)",dedup);
   db.update("insert into analytics_legacy_dedup(dedup_key,expires_at) values(?,?)",fresh,java.sql.Timestamp.from(now.plusSeconds(600)));
  });
  service.expire();
  assertThat(db.queryForObject("select count(*) from guest_insight_events where restaurant_id=? and received_day<?",Long.class,restaurant.getId(),received.minusDays(89))).isZero();
  assertThat(rows()).as("current event plus oldest permitted UTC day remain").isEqualTo(2);
  assertThat(db.queryForObject("select count(*) from analytics_legacy_dedup where dedup_key like ?",Long.class,prefix+"%")).isZero();
  assertThat(db.queryForObject("select count(*) from analytics_legacy_dedup where dedup_key=?",Long.class,fresh)).isEqualTo(1);
 }
 @Test void categoryObservationsRejectFreeTextAndOtherRestaurantsCategoryButAcceptOwnedKey(){
  Category owned=Category.builder().restaurantId(restaurant.getId()).name("Synthetic starter selection").build();
  Category foreign=Category.builder().restaurantId(other.getId()).name("Other restaurant selection").build();
  tx.executeWithoutResult(status->{
   var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();
   db.update(connection->{var statement=connection.prepareStatement("insert into categories(restaurant_id,name,display_order,is_active) values(?,?,0,true)",new String[]{"id"});statement.setLong(1,owned.getRestaurantId());statement.setString(2,owned.getName());return statement;},holder);owned.setId(Objects.requireNonNull(holder.getKey()).longValue());
   var otherHolder=new org.springframework.jdbc.support.GeneratedKeyHolder();
   db.update(connection->{var statement=connection.prepareStatement("insert into categories(restaurant_id,name,display_order,is_active) values(?,?,0,true)",new String[]{"id"});statement.setLong(1,foreign.getRestaurantId());statement.setString(2,foreign.getName());return statement;},otherHolder);foreign.setId(Objects.requireNonNull(otherHolder.getKey()).longValue());
  });
  for(String category:List.of("arbitrary guest note",String.valueOf(foreign.getId()),"https://example.test/untrusted"))assertThatThrownBy(()->service.record(new GuestInsightService.Input(restaurant.getSlug(),UUID.randomUUID().toString(),"CATEGORY_SELECTED",null,null,category),ip())).isInstanceOf(RuntimeException.class);
  service.record(new GuestInsightService.Input(restaurant.getSlug(),UUID.randomUUID().toString(),"CATEGORY_SELECTED",null,null,String.valueOf(owned.getId())),ip());
  assertThat(rows()).isEqualTo(1);assertThat(total(report(),"CATEGORY_SELECTED")).isEqualTo(1);
 }

 @Test void suspensionCommittedBeforeTenantLockDeniesAnInitiallyVisibleRestaurant() throws Exception {
  CountDownLatch beforeLock=new CountDownLatch(1),continueLock=new CountDownLatch(1);
  java.util.concurrent.atomic.AtomicBoolean first=new java.util.concurrent.atomic.AtomicBoolean(true);
  long restaurantId=restaurant.getId();
  // The answer still executes an actual locked JPQL read in the request transaction.
  // Spring Data's abstract finder cannot use Mockito callRealMethod.
  org.mockito.Mockito.doAnswer(invocation->{
   Long id=invocation.getArgument(0);
   if(id==restaurantId&&first.compareAndSet(true,false)){
    beforeLock.countDown();
    if(!continueLock.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Concurrent suspension was not released");
   }
   return entityManager.createQuery("select r from Restaurant r where r.id=:id",Restaurant.class).setParameter("id",id).setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).getResultList().stream().findFirst();
  }).when(restaurants).findByIdForUpdate(org.mockito.ArgumentMatchers.anyLong());
  ExecutorService request=Executors.newSingleThreadExecutor();
  try{
   Future<?> ingestion=request.submit(()->record("MENU_VISIT"));
   assertThat(beforeLock.await(15,TimeUnit.SECONDS)).as("request reached its authoritative tenant lock").isTrue();
   tx.executeWithoutResult(status->db.update("update restaurants set status='SUSPENDED' where id=?",restaurantId));
   continueLock.countDown();
   assertThatThrownBy(()->ingestion.get(20,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(com.atelier.media.exception.RestaurantNotFoundException.class);
   assertThat(rows()).as("revoked public access cannot record an observation").isZero();
  }finally{continueLock.countDown();request.shutdownNow();}
 }
}
