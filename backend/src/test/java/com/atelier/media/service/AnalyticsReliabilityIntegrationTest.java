package com.atelier.media.service;
import com.atelier.media.dto.AnalyticsEventDto;
import com.atelier.media.model.*;
import com.atelier.media.repository.*;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real isolated H2 transactions; storage mocked, no PostgreSQL inference. */
@SpringBootTest(properties={
 "spring.config.import=optional:classpath:analytics-reliability-no-secrets.properties",
 "spring.datasource.url=jdbc:h2:mem:sprint5_analytics_reliability;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.flyway.locations=classpath:db/migration/h2","atelier.scheduling.enabled=false",
 "aws.access-key-id=AKIAIOSFODNN7EXAMPLE","aws.secret-access-key=synthetic-test-secret-not-for-aws",
 "aws.region=us-east-1","aws.s3.bucket=atelier-test-bucket"
})
@ActiveProfiles("test")
class AnalyticsReliabilityIntegrationTest {
 @Autowired RestaurantRepository restaurants;
 @Autowired MediaRepository media;
 @Autowired AnalyticsEventRepository events;
 @Autowired AnalyticsDailyRepository daily;
 @Autowired JdbcTemplate db;
 @Autowired TransactionTemplate tx;
 @MockBean S3Storage storage;
 Restaurant restaurant;Media dish;AnalyticsService service;
 @BeforeEach void seed(){restaurant=TestFixtures.restaurant(1,"analytics-"+UUID.randomUUID());restaurant.setId(null);restaurant=restaurants.saveAndFlush(restaurant);dish=TestFixtures.media(1,restaurant.getId(),restaurant.getSlug(),UploadStatus.COMPLETED);dish.setId(null);dish.setVersion(null);dish=media.saveAndFlush(dish);service=withExecutor(Runnable::run);}
 private AnalyticsService withExecutor(Executor executor){return new AnalyticsService(restaurants,media,events,daily,tx,executor);}
 private AnalyticsEventDto menu(){return new AnalyticsEventDto(restaurant.getSlug(),null,"MENU_VIEW");}
 private long rows(){return events.countByRestaurantIdAndEventType(restaurant.getId(),"MENU_VIEW");}
 private String ip(){return "synthetic-client-"+UUID.randomUUID();}
 private long count(){return db.queryForObject("select coalesce(sum(event_count),0) from analytics_daily where restaurant_id=? and event_type='MENU_VIEW'",Long.class,restaurant.getId());}
 private void reject(String name){tx.executeWithoutResult(s->db.execute("alter table analytics_events add constraint "+name+" check(restaurant_id<>"+restaurant.getId()+")"));}
 private void allow(String name){tx.executeWithoutResult(s->db.execute("alter table analytics_events drop constraint "+name));}

 @Test void successfulReturnMeansTheEventIsDurablyCommittedRatherThanQueued(){List<Runnable> queued=new ArrayList<>();AnalyticsService controlled=withExecutor(queued::add);controlled.recordEvent(menu(),ip());assertThat(rows()).as("ingestion must commit before successful return").isEqualTo(1);assertThat(count()).isEqualTo(1);}
 @Test void persistenceFailureIsReportedToTheCaller(){String name="reject_analytics_"+restaurant.getId();reject(name);try{assertThatThrownBy(()->service.recordEvent(menu(),ip())).isInstanceOf(RuntimeException.class);}finally{allow(name);}assertThat(rows()).isZero();assertThat(count()).isZero();assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getMenuViewsCount()).isZero();}
 @Test void failedTransactionDoesNotPoisonTheSameEventRetry(){String name="retry_analytics_"+restaurant.getId(),client=ip();reject(name);try{try{service.recordEvent(menu(),client);}catch(RuntimeException expected){/* retry after a visible persistence failure */}}finally{allow(name);}service.recordEvent(menu(),client);assertThat(rows()).as("rolled-back event remains retryable").isEqualTo(1);assertThat(count()).isEqualTo(1);assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getMenuViewsCount()).isEqualTo(1);}
 @Test void concurrentAcceptedEventsExactlyMatchRawRowsCountersAndDailyAggregate()throws Exception{int n=12;CountDownLatch start=new CountDownLatch(1);ExecutorService workers=Executors.newFixedThreadPool(n);try{List<Future<?>> futures=new ArrayList<>();for(int i=0;i<n;i++)futures.add(workers.submit(()->{start.await();service.recordEvent(menu(),ip());return null;}));start.countDown();for(Future<?> f:futures)f.get(30,TimeUnit.SECONDS);}finally{workers.shutdownNow();}assertThat(rows()).isEqualTo(n);assertThat(count()).isEqualTo(n);assertThat(restaurants.findById(restaurant.getId()).orElseThrow().getMenuViewsCount()).isEqualTo(n);}
 @Test void sequentialLegacyDeduplicationDoesNotIncrementTwice(){String client=ip();service.recordEvent(menu(),client);service.recordEvent(menu(),client);assertThat(rows()).isEqualTo(1);assertThat(count()).isEqualTo(1);}
 @Test void receivedUtcDayIsUsedWithoutClaimingUniqueVisitors(){service.recordEvent(menu(),ip());var buckets=daily.findByRestaurantIdAndDayBetween(restaurant.getId(),LocalDate.now(ZoneOffset.UTC),LocalDate.now(ZoneOffset.UTC));assertThat(buckets).hasSize(1);assertThat(buckets.getFirst().getDishId()).isZero();assertThat(buckets.getFirst().getEventCount()).isEqualTo(1);}
 @Test void suspendedRestaurantCannotAcceptGuestAnalytics(){restaurant.setStatus(RestaurantStatus.SUSPENDED);restaurants.saveAndFlush(restaurant);assertThatThrownBy(()->service.recordEvent(menu(),ip())).isInstanceOf(RuntimeException.class);assertThat(rows()).isZero();}
 @Test void unpublishedRestaurantCannotAcceptGuestAnalytics(){restaurant.setIsMenuLive(false);restaurants.saveAndFlush(restaurant);assertThatThrownBy(()->service.recordEvent(menu(),ip())).isInstanceOf(RuntimeException.class);assertThat(rows()).isZero();}
 @Test void unavailableDishCannotReceiveGuestDetailOrModelEvents(){dish.setAvailable(false);media.saveAndFlush(dish);assertThatThrownBy(()->service.recordEvent(new AnalyticsEventDto(restaurant.getSlug(),dish.getId(),"MODEL_3D_VIEW"),ip())).isInstanceOf(RuntimeException.class);assertThat(events.countByRestaurantIdAndEventType(restaurant.getId(),"MODEL_3D_VIEW")).isZero();}
 @Test void foreignDishCannotReceiveAnotherRestaurantsAnalytics(){Restaurant other=TestFixtures.restaurant(1,"other-analytics-"+UUID.randomUUID());other.setId(null);other=restaurants.saveAndFlush(other);Media foreign=TestFixtures.media(1,other.getId(),other.getSlug(),UploadStatus.COMPLETED);foreign.setId(null);foreign.setVersion(null);foreign=media.saveAndFlush(foreign);long id=foreign.getId();assertThatThrownBy(()->service.recordEvent(new AnalyticsEventDto(restaurant.getSlug(),id,"MODEL_3D_VIEW"),ip())).isInstanceOf(RuntimeException.class);assertThat(events.countByRestaurantIdAndEventType(restaurant.getId(),"MODEL_3D_VIEW")).isZero();}
}
