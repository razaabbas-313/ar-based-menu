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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Disposable real H2 transactions; all storage interactions are mocked. */
@SpringBootTest(properties = {
        "spring.config.import=optional:classpath:guest-product-no-secrets.properties",
        "spring.datasource.url=jdbc:h2:mem:guest_product;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.flyway.locations=classpath:db/migration/h2",
        "atelier.scheduling.enabled=false", "aws.access-key-id=AKIAIOSFODNN7EXAMPLE",
        "aws.secret-access-key=synthetic-test-secret-not-for-aws", "aws.region=us-east-1", "aws.s3.bucket=atelier-test-bucket"
})
@ActiveProfiles("test")
class GuestExperienceIntegrationTest {
    @Autowired GuestExperienceService service;
    @Autowired EntitlementService entitlements;
    @Autowired RestaurantRepository restaurants;
    @SpyBean MediaRepository media;
    @Autowired PlatformUserRepository admins;
    @Autowired PublicMenuService menus;
    @Autowired TransactionTemplate tx;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockBean S3Storage storage;
    Restaurant restaurant;
    Restaurant other;
    Media dish;
    Media second;
    PlatformUser admin;
    private static final AtomicInteger CLIENT = new AtomicInteger();
    private static final String ORDER_NUMBER="923001234567";
    private static final String WAITER_NUMBER="923009876543";

    @BeforeEach void seed() {
        restaurant=restaurant("guest-"+UUID.randomUUID());
        other=restaurant("other-"+UUID.randomUUID());
        dish=TestFixtures.mediaWithModel(1,restaurant.getId(),restaurant.getSlug());dish.setId(null);dish.setVersion(null);dish=media.saveAndFlush(dish);
        second=TestFixtures.media(2,restaurant.getId(),restaurant.getSlug(),UploadStatus.COMPLETED);second.setId(null);second.setVersion(null);second.setPriceTag(new BigDecimal("2.35"));second=media.saveAndFlush(second);
        admin=admins.saveAndFlush(PlatformUser.builder().email(UUID.randomUUID()+"@platform.test").displayName("Guest product operator")
                .passwordHash("synthetic-test-placeholder").mustChangePassword(false).build());
        when(storage.bucket()).thenReturn("atelier-test-bucket");
        when(storage.tenantPrefix(anyString())).thenAnswer(i->"restaurant-atelier/"+i.getArgument(0)+"/");
        when(storage.privateTenantPrefix(anyString())).thenAnswer(i->"restaurant-atelier-private/"+i.getArgument(0)+"/");
    }

    @Test void legacyRestaurantKeepsExistingArViewingAndVenueEntry() {
        var context=service.publicContext(restaurant.getSlug(),null);
        assertThat(context.get("menuMode")).isEqualTo("AR");
        assertThat(menus.getMenu(restaurant.getSlug()).items()).hasSize(2);
        assertThat(context.get("tableLabel")).isNull();
        assertThat(context.get("orderingAvailable")).isEqualTo(false);
        assertThat(context.get("waiterAvailable")).isEqualTo(false);
    }
    @Test void simpleModePreservesAllDishesButRemovesModelLinksAndAr() {
        save("SIMPLE",false,false,ORDER_NUMBER,null,"Please assist our table.");
        var menu=menus.getMenu(restaurant.getSlug());
        assertThat(menu.items()).hasSize(2);
        assertThat(menu.items()).allSatisfy(item->{assertThat(item.is3d()).isFalse();assertThat(item.modelUrl()).isNull();assertThat(item.hasArSupport()).isFalse();});
        assertThat(service.publicContext(restaurant.getSlug(),null).get("menuMode")).isEqualTo("SIMPLE");
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isNotNull();
    }
    @Test void threeDModeKeepsPhotoOnlyDishesAndModelViewingWithoutAr() {
        save("THREE_D",false,false,ORDER_NUMBER,null,"Please assist our table.");
        var menu=menus.getMenu(restaurant.getSlug());
        assertThat(menu.items()).hasSize(2);
        assertThat(menu.items().stream().filter(i->i.id().equals(dish.getId())).findFirst().orElseThrow().is3d()).isTrue();
        assertThat(menu.items()).allSatisfy(i->assertThat(i.hasArSupport()).isFalse());
        assertThat(service.publicContext(restaurant.getSlug(),null).get("menuMode")).isEqualTo("THREE_D");
    }
    @Test void ownerCannotSelectDisallowedMode() {
        grant("model.ownerUpload",false);grant("model.concierge",false);grant("menu.allowedModes",List.of("SIMPLE"));
        assertThatThrownBy(()->save("AR",false,false,ORDER_NUMBER,null,"Please assist our table.")).isInstanceOf(RuntimeException.class);
        assertThat(service.publicContext(restaurant.getSlug(),null).get("menuMode")).isEqualTo("SIMPLE");
    }
    @Test void explicitModeDowngradeFallsBackSafelyWithoutDeletingModel() {
        save("AR",false,false,ORDER_NUMBER,null,"Please assist our table.");
        grant("model.ownerUpload",false);grant("model.concierge",false);grant("menu.allowedModes",List.of("SIMPLE","THREE_D"));
        assertThat(service.publicContext(restaurant.getSlug(),null).get("menuMode")).isEqualTo("SIMPLE");
        assertThat(media.findById(dish.getId()).orElseThrow().getModelS3Key()).isNotNull();
    }
    @Test void staleSettingsVersionCannotOverwriteCurrentConfiguration() {
        long stale=settingsVersion();save("SIMPLE",false,false,ORDER_NUMBER,null,"Please assist our table.");
        assertThatThrownBy(()->service.saveSettings(tenant(),new GuestExperienceService.SettingsWrite(stale,"AR",false,false,ORDER_NUMBER,null,"Stale message"))).isInstanceOf(RuntimeException.class);
        assertThat(service.publicContext(restaurant.getSlug(),null).get("menuMode")).isEqualTo("SIMPLE");
    }
    @Test void newOperationalFeaturesStillRequireExplicitEntitlementAndOwnerOptIn() {
        assertThatThrownBy(()->save("AR",true,true,ORDER_NUMBER,null,"Please assist our table.")).isInstanceOf(RuntimeException.class);
        grant("ordering.enabled",true);grant("waiter.enabled",true);
        assertThat(service.publicContext(restaurant.getSlug(),null).get("orderingAvailable")).isEqualTo(false);
        save("AR",true,true,ORDER_NUMBER,null,"Please assist our table.");
        var context=service.publicContext(restaurant.getSlug(),null);
        assertThat(context.get("orderingAvailable")).isEqualTo(true);assertThat(context.get("waiterAvailable")).isEqualTo(true);
    }

    @Test void tableIdentityIsOpaqueAndScopedContextDoesNotEchoToken() {
        tableEntitled();var table=service.createTable(tenant(),"Window 7");String token=token(table);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(token).doesNotContain("Window");
        var context=service.publicContext(restaurant.getSlug(),token);
        assertThat(context.get("tableLabel")).isEqualTo("Window 7");
        assertThat(context.get("contextError")).isNull();
        assertThat(context.toString()).doesNotContain(token,"tokenHash","qrToken");
    }
    @Test void tableRenameKeepsExistingPrintedIdentity() {
        tableEntitled();var table=service.createTable(tenant(),"Table 1");String token=token(table);
        service.updateTable(tenant(),number(table,"id"),new GuestExperienceService.TableWrite("Garden 1",true,number(table,"version")));
        assertThat(service.publicContext(restaurant.getSlug(),token).get("tableLabel")).isEqualTo("Garden 1");
    }
    @Test void rotationRevokesOldQrAndDoesNotChangeVenueEntry() {
        tableEntitled();var first=service.createTable(tenant(),"Table 1");
        var rotated=service.rotateTable(tenant(),number(first,"id"),number(first,"version"));
        assertThat(token(rotated)).isNotEqualTo(token(first));
        invalidContext(restaurant.getSlug(),token(first));
        assertThat(service.publicContext(restaurant.getSlug(),token(rotated)).get("tableLabel")).isEqualTo("Table 1");
        assertThat(service.publicContext(restaurant.getSlug(),null).get("contextError")).isNull();
    }
    @Test void disabledTableNoLongerGrantsContextOrGuestActions() {
        operational();tableEntitled();var table=service.createTable(tenant(),"Table 1");
        service.updateTable(tenant(),number(table,"id"),new GuestExperienceService.TableWrite("Table 1",false,number(table,"version")));
        invalidContext(restaurant.getSlug(),token(table));
        assertThatThrownBy(()->prepare(token(table),oneLine())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->service.waiter(restaurant.getSlug(),new GuestExperienceService.WaiterWrite(token(table)),ip())).isInstanceOf(RuntimeException.class);
    }
    @Test void foreignTableIdentityFailsClosedUnderAnotherRestaurant() {
        operational();tableEntitled();var table=service.createTable(tenant(),"Table 1");
        invalidContext(other.getSlug(),token(table));
        assertThatThrownBy(()->service.updateTable(otherTenant(),number(table,"id"),new GuestExperienceService.TableWrite("Stolen",false,number(table,"version")))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->service.rotateTable(otherTenant(),number(table,"id"),number(table,"version"))).isInstanceOf(RuntimeException.class);
        assertThat(service.tables(otherTenant())).isEmpty();
    }
    @Test void tableQrRevocationThroughAdminPolicyTakesEffectOnFreshGuestActions() {
        operational();tableEntitled();var table=service.createTable(tenant(),"Table 1");grant("qr.table",false);
        invalidContext(restaurant.getSlug(),token(table));
        assertThatThrownBy(()->prepare(token(table),oneLine())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->service.createTable(tenant(),"Table 2")).isInstanceOf(RuntimeException.class);
    }
    @Test void guessedAndMalformedTableInputsDoNotBecomeTrustedLabels() {
        for(String token:List.of("1","Table 8","../../other","x".repeat(1000)))invalidContext(restaurant.getSlug(),token);
    }
    @Test void tableCreationRequiresEntitlement() {
        assertThatThrownBy(()->service.createTable(tenant(),"Table 1")).isInstanceOf(RuntimeException.class);
    }
    @Test void tableQuotaCountsActiveTablesAndReactivationUsesSameGate() {
        tableEntitled();grant("quota.tables",1);var first=service.createTable(tenant(),"Table 1");
        assertThatThrownBy(()->service.createTable(tenant(),"Table 2")).isInstanceOf(RuntimeException.class);
        var disabled=service.updateTable(tenant(),number(first,"id"),new GuestExperienceService.TableWrite("Table 1",false,number(first,"version")));
        service.createTable(tenant(),"Table 2");
        assertThatThrownBy(()->service.updateTable(tenant(),number(first,"id"),new GuestExperienceService.TableWrite("Table 1",true,number(disabled,"version")))).isInstanceOf(RuntimeException.class);
    }
    @Test void concurrentTableCreationCannotExceedFinalActiveSlot() throws Exception {
        tableEntitled();grant("quota.tables",1);
        ExecutorService pool=Executors.newFixedThreadPool(6);var start=new CountDownLatch(1);var successes=new AtomicInteger();
        List<Future<?>> futures=new ArrayList<>();
        try {
            for(int i=0;i<6;i++){final int index=i;futures.add(pool.submit(()->{start.await(10,TimeUnit.SECONDS);try{service.createTable(tenant(),"Concurrent "+index);successes.incrementAndGet();}catch(RuntimeException denied){/* only one quota winner */}return null;}));}
            start.countDown();for(Future<?> future:futures)future.get(20,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertThat(successes.get()).isOne();assertThat(service.tables(tenant()).stream().filter(t->Boolean.TRUE.equals(t.get("active"))).count()).isOne();
    }
    @Test void duplicateNormalizedNamesAndStaleTableEditsAreRejected() {
        tableEntitled();var table=service.createTable(tenant(),"Table 1");
        assertThatThrownBy(()->service.createTable(tenant()," table 1 ")).isInstanceOf(RuntimeException.class);
        service.updateTable(tenant(),number(table,"id"),new GuestExperienceService.TableWrite("Renamed",true,number(table,"version")));
        assertThatThrownBy(()->service.updateTable(tenant(),number(table,"id"),new GuestExperienceService.TableWrite("Stale",false,number(table,"version")))).isInstanceOf(RuntimeException.class);
    }

    @Test void authoritativeSnapshotCalculatesDecimalPricesAndOnlyClaimsPreparedRequest() {
        operational();var snapshot=prepare(null,List.of(line(dish,2),line(second,3)));
        assertThat(snapshot.get("estimatedTotal")).isEqualTo("43.05");
        assertThat(snapshot.get("status")).isEqualTo("PREPARED_REQUEST");
        assertThat(snapshot.get("reference")).isNotNull();assertThat(snapshot.get("recipient")).isEqualTo(ORDER_NUMBER);
        assertThat(snapshot.get("restaurantName")).isEqualTo(restaurant.getName());
        assertThat((List<?>)snapshot.get("lines")).hasSize(2);
        assertThat(snapshot).doesNotContainKeys("acceptedAt","deliveredAt","paymentStatus");
    }
    @Test void tableSnapshotUsesCurrentValidatedLabelRatherThanClientNumber() {
        operational();tableEntitled();var table=service.createTable(tenant(),"Garden 9");
        var snapshot=prepare(token(table),oneLine());assertThat(snapshot.get("tableLabel")).isEqualTo("Garden 9");
        assertThat(snapshot.toString()).doesNotContain(token(table),"tokenHash");
    }
    @Test void serverDoesNotAcceptStaleOrTamperedPrices() {
        operational();
        var stale=new GuestExperienceService.OrderLine(dish.getId(),1,new BigDecimal("0.01"),null);
        assertThatThrownBy(()->prepare(null,List.of(stale))).isInstanceOf(RuntimeException.class);
    }
    @Test void missingExpectedPriceStillUsesAuthoritativeServerValue() {
        operational();var snapshot=prepare(null,List.of(new GuestExperienceService.OrderLine(dish.getId(),2,null,null)));
        assertThat(snapshot.get("estimatedTotal")).isEqualTo("36.00");
    }
    @Test void unavailableOrUnpublishedDishCannotBeOrdered() {
        operational();tx.executeWithoutResult(s->media.findById(dish.getId()).orElseThrow().setAvailable(false));
        assertThatThrownBy(()->prepare(null,oneLine())).isInstanceOf(RuntimeException.class);
        tx.executeWithoutResult(s->{Media changed=media.findById(dish.getId()).orElseThrow();changed.setAvailable(true);changed.setUploadStatus(UploadStatus.PRESIGNED);});
        assertThatThrownBy(()->prepare(null,oneLine())).isInstanceOf(RuntimeException.class);
    }
    @Test void foreignDishDoesNotEnterAnotherRestaurantsSnapshot() {
        operational();Media foreign=TestFixtures.media(8,other.getId(),other.getSlug(),UploadStatus.COMPLETED);foreign.setId(null);foreign.setVersion(null);foreign=media.saveAndFlush(foreign);
        List<GuestExperienceService.OrderLine> items=List.of(new GuestExperienceService.OrderLine(foreign.getId(),1,null,null));
        assertThatThrownBy(()->prepare(null,items)).isInstanceOf(RuntimeException.class);
    }
    @Test void quantitiesCartBoundsDuplicatesAndNotesAreValidated() {
        operational();
        for(Integer quantity:Arrays.asList(null,0,-1,100)) {
            var invalid=new GuestExperienceService.OrderLine(dish.getId(),quantity,null,null);
            assertThatThrownBy(()->prepare(null,List.of(invalid))).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(()->prepare(null,List.of())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->prepare(null,List.of(line(dish,1),line(dish,2)))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->service.prepareOrder(restaurant.getSlug(),new GuestExperienceService.OrderPrepare(oneLine(),"n".repeat(501),null),ip())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->prepare(null,List.of(new GuestExperienceService.OrderLine(dish.getId(),1,null,"n".repeat(201))))).isInstanceOf(RuntimeException.class);
    }
    @Test void disablingOrderingAfterOwnerConfigurationStopsNewPreparations() {
        operational();grant("ordering.enabled",false);
        assertThat(service.publicContext(restaurant.getSlug(),null).get("orderingAvailable")).isEqualTo(false);
        assertThatThrownBy(()->prepare(null,oneLine())).isInstanceOf(RuntimeException.class);
    }
    @Test void invalidInternationalContactNumbersAreRejected() {
        grant("ordering.enabled",true);grant("waiter.enabled",true);
        for(String invalid:List.of("123","03001234567","++923001234567","923001234567 ext 9"))
            assertThatThrownBy(()->save("AR",true,true,invalid,null,"Please assist our table.")).isInstanceOf(RuntimeException.class);
    }
    @Test void internationalLeadingPlusIsNormalizedWithoutClaimingOwnershipVerification() {
        grant("ordering.enabled",true);grant("waiter.enabled",true);save("AR",true,true,"+"+ORDER_NUMBER,null,"Please assist our table.");
        assertThat(prepare(null,oneLine()).get("recipient")).isEqualTo(ORDER_NUMBER);
    }
    @Test void waiterSupportsSeparateRecipientAndValidatedTableLabel() {
        operational();tableEntitled();var table=service.createTable(tenant(),"Terrace 4");
        var handoff=service.waiter(restaurant.getSlug(),new GuestExperienceService.WaiterWrite(token(table)),ip());
        assertThat(handoff.get("recipient")).isEqualTo(WAITER_NUMBER);
        assertThat((String)handoff.get("message")).contains("Terrace 4");
        assertThat((String)handoff.get("url")).startsWith("https://wa.me/"+WAITER_NUMBER+"?text=");
        assertThat(handoff).doesNotContainKeys("deliveredAt","acceptedAt");
    }
    @Test void waiterFallsBackToOrderRecipientForGeneralVenueAssistance() {
        grant("ordering.enabled",true);grant("waiter.enabled",true);save("AR",true,true,ORDER_NUMBER,null,"Please help us.");
        var handoff=service.waiter(restaurant.getSlug(),new GuestExperienceService.WaiterWrite(null),ip());
        assertThat(handoff.get("recipient")).isEqualTo(ORDER_NUMBER);assertThat((String)handoff.get("message")).doesNotContain("null","undefined");
    }
    @Test void disabledWaiterPermissionStopsHandoffsEvenWithSavedNumber() {
        operational();grant("waiter.enabled",false);
        assertThat(service.publicContext(restaurant.getSlug(),null).get("waiterAvailable")).isEqualTo(false);
        assertThatThrownBy(()->service.waiter(restaurant.getSlug(),new GuestExperienceService.WaiterWrite(null),ip())).isInstanceOf(RuntimeException.class);
    }
    @Test void suspendedAndPrivateRestaurantsCannotPrepareGuestActions() {
        operational();tx.executeWithoutResult(s->restaurants.findById(restaurant.getId()).orElseThrow().setIsMenuLive(false));
        assertThatThrownBy(()->prepare(null,oneLine())).isInstanceOf(RuntimeException.class);
        tx.executeWithoutResult(s->{Restaurant changed=restaurants.findById(restaurant.getId()).orElseThrow();changed.setIsMenuLive(true);changed.setStatus(RestaurantStatus.SUSPENDED);});
        assertThatThrownBy(()->prepare(null,oneLine())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->service.waiter(restaurant.getSlug(),new GuestExperienceService.WaiterWrite(null),ip())).isInstanceOf(RuntimeException.class);
    }
    @Test void revokedOwnerCannotChangeSettingsOrCreateTables() {
        tableEntitled();tx.executeWithoutResult(s->restaurants.findById(restaurant.getId()).orElseThrow().incrementTokenVersion());
        assertThatThrownBy(()->service.createTable(tenant(),"Forbidden")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->save("SIMPLE",false,false,ORDER_NUMBER,null,"Please assist our table.")).isInstanceOf(RuntimeException.class);
    }
    @Test void preparedValuesRemainHistoricalWhenCurrentPriceAndTableLabelChange() {
        operational();tableEntitled();var table=service.createTable(tenant(),"Window 2");
        var prepared=prepare(token(table),oneLine());
        tx.executeWithoutResult(s->media.findById(dish.getId()).orElseThrow().setPriceTag(new BigDecimal("20.00")));
        service.updateTable(tenant(),number(table,"id"),new GuestExperienceService.TableWrite("Garden 2",true,number(table,"version")));
        assertThat(prepared.get("estimatedTotal")).isEqualTo("18.00");assertThat(prepared.get("tableLabel")).isEqualTo("Window 2");
        var fresh=prepare(token(table),List.of(new GuestExperienceService.OrderLine(dish.getId(),1,null,null)));
        assertThat(fresh.get("estimatedTotal")).isEqualTo("20.00");assertThat(fresh.get("tableLabel")).isEqualTo("Garden 2");
    }
    @Test void repeatedAnonymousPreparationsAreBoundedWithoutAnyDeliveryClaim() {
        operational();int rejected=0;
        for(int n=0;n<30;n++)try {
            var result=service.prepareOrder(restaurant.getSlug(),new GuestExperienceService.OrderPrepare(oneLine(),null,null),"203.0.113.250");
            assertThat(result.get("status")).isEqualTo("PREPARED_REQUEST");
        } catch(RuntimeException limited){rejected++;}
        assertThat(rejected).isPositive();
    }

    @Test void nullOrderLineProducesControlledFieldValidationFailure() {
        operational();
        assertThatThrownBy(()->prepare(null,Arrays.asList((GuestExperienceService.OrderLine)null)))
                .isInstanceOf(com.atelier.media.exception.InvalidFieldException.class);
    }

    @Test void concurrentPriceEditCannotMixVersionsWithinPreparedSnapshot() throws Exception {
        operational();
        var secondRead=new CountDownLatch(1);var edited=new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation->{
            secondRead.countDown();
            if(!edited.await(10,TimeUnit.SECONDS))throw new AssertionError("Concurrent edit did not commit");
            // Spring Data's derived method is abstract to Mockito; execute its equivalent
            // scoped database lookup in the service's existing snapshot transaction.
            return entityManager.createQuery("select m from Media m where m.id=:id and m.restaurantId=:tenant",Media.class)
                    .setParameter("id",second.getId()).setParameter("tenant",restaurant.getId()).getResultStream().findFirst();
        }).when(media).findByIdAndRestaurantId(second.getId(),restaurant.getId());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var snapshot=pool.submit(()->prepare(null,List.of(line(dish,1),line(second,1))));
            assertThat(secondRead.await(10,TimeUnit.SECONDS)).as("snapshot read first price before concurrent edit").isTrue();
            var update=pool.submit(()->tx.executeWithoutResult(s->{
                media.findById(dish.getId()).orElseThrow().setPriceTag(new BigDecimal("25.00"));
                media.findById(second.getId()).orElseThrow().setPriceTag(new BigDecimal("9.00"));
            }));
            update.get(10,TimeUnit.SECONDS);edited.countDown();
            assertThat(snapshot.get(10,TimeUnit.SECONDS).get("estimatedTotal")).isEqualTo("20.35");
            assertThat(media.findById(dish.getId()).orElseThrow().getPriceTag()).isEqualByComparingTo("25.00");
            assertThat(media.findById(second.getId()).orElseThrow().getPriceTag()).isEqualByComparingTo("9.00");
        } finally {edited.countDown();pool.shutdownNow();}
    }

    @Test void ownerCanPagePastTwoHundredTablesWithoutCrossTenantResults() {
        tableEntitled();
        tx.executeWithoutResult(s->{
            for(int i=0;i<205;i++) {
                String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().substring(0,32).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                db.update("insert into restaurant_tables(restaurant_id,name,normalized_name,qr_token,token_hash) values(?,?,?,?,?)",
                        restaurant.getId(),"Page "+i,"page "+i,raw,UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));
            }
            db.update("insert into restaurant_tables(restaurant_id,name,normalized_name,qr_token,token_hash) values(?,?,?,?,?)",
                    other.getId(),"Foreign page","foreign page","x".repeat(43),UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));
        });
        var first=service.tables(tenant());assertThat(first).hasSize(200);
        var secondPage=service.tables(tenant(),number(first.get(199),"id"));assertThat(secondPage).hasSize(5);
        var ids=new HashSet<Long>();first.forEach(row->ids.add(number(row,"id")));secondPage.forEach(row->ids.add(number(row,"id")));
        assertThat(ids).hasSize(205);
        assertThat(secondPage).allSatisfy(row->assertThat((String)row.get("name")).startsWith("Page "));
        assertThat(service.tables(tenant(),number(secondPage.get(4),"id"))).isEmpty();
    }

    private Restaurant restaurant(String slug){Restaurant r=TestFixtures.restaurant(1,slug);r.setId(null);return restaurants.saveAndFlush(r);}
    private Tenant tenant(){return new Tenant(restaurant.getId(),restaurant.getSlug(),false,restaurant.getTokenVersion());}
    private Tenant otherTenant(){return new Tenant(other.getId(),other.getSlug(),false,other.getTokenVersion());}
    private AdminPrincipal principal(){return new AdminPrincipal(admin.getId(),admin.getEmail(),admin.getTokenVersion());}
    private long settingsVersion(){return number(service.settings(tenant()),"version");}
    private void grant(String key,Object value){long version=number(entitlements.effective(restaurant.getId(),true),"configVersion");entitlements.override(restaurant.getId(),key,new EntitlementService.OverrideWrite(value,version,"Synthetic guest feature configuration",null),principal());}
    private void tableEntitled(){grant("qr.table",true);}
    private void operational(){grant("ordering.enabled",true);grant("waiter.enabled",true);save("AR",true,true,ORDER_NUMBER,WAITER_NUMBER,"Please assist our table.");}
    private Map<String,Object> save(String mode,boolean ordering,boolean waiter,String orderNumber,String waiterNumber,String message){return service.saveSettings(tenant(),new GuestExperienceService.SettingsWrite(settingsVersion(),mode,ordering,waiter,orderNumber,waiterNumber,message));}
    private GuestExperienceService.OrderLine line(Media item,int quantity){return new GuestExperienceService.OrderLine(item.getId(),quantity,item.getPriceTag(),null);}
    private List<GuestExperienceService.OrderLine> oneLine(){return List.of(line(dish,1));}
    private Map<String,Object> prepare(String token,List<GuestExperienceService.OrderLine> items){return service.prepareOrder(restaurant.getSlug(),new GuestExperienceService.OrderPrepare(items,"Synthetic guest note",token),ip());}
    private void invalidContext(String slug,String token){Map<String,Object> context=service.publicContext(slug,token);assertThat(context.get("contextError")).isNotNull();assertThat(context.get("orderingAvailable")).isEqualTo(false);assertThat(context.get("waiterAvailable")).isEqualTo(false);}
    private static String token(Map<String,Object> table){return (String)table.get("qrToken");}
    private static long number(Map<String,Object> map,String key){return ((Number)map.get(key)).longValue();}
    private static String ip(){return "198.51.100."+CLIENT.incrementAndGet();}
}
