package com.atelier.media.service;

import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dishseqtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.locations=classpath:db/migration/h2"
})
class DishSequenceConcurrencyTest {

    @Autowired
    private DishService dishService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Test
    void fifteenConcurrentAllocationsProduceUniqueStrictlySequentialCodes() throws Exception {
        Restaurant restaurant = Restaurant.builder()
                .name("Seq Bistro")
                .slug("seq-bistro-" + System.currentTimeMillis())
                .ownerName("Owner")
                .email("owner-" + System.currentTimeMillis() + "@platform.test")
                .passwordHash("hash")
                .status(RestaurantStatus.ACTIVE)
                .nextDishSeq(1L)
                .build();
        restaurant = restaurantRepository.saveAndFlush(restaurant);
        final Long restaurantId = restaurant.getId();

        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<String> allocatedCodes = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                String code = dishService.allocateNextDishCode(restaurantId);
                allocatedCodes.add(code);
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(allocatedCodes).hasSize(15);
        assertThat(allocatedCodes).doesNotHaveDuplicates();

        List<String> sorted = allocatedCodes.stream().sorted().toList();
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            expected.add(String.format("DSH-%04d", i));
        }
        assertThat(sorted).isEqualTo(expected);

        Restaurant updated = restaurantRepository.findById(restaurantId).orElseThrow();
        assertThat(updated.getNextDishSeq()).isEqualTo(16L);
    }
}
