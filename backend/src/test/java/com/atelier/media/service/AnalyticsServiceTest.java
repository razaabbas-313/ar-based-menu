package com.atelier.media.service;

import com.atelier.media.dto.AnalyticsEventDto;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.exception.TooManyAttemptsException;
import com.atelier.media.model.AnalyticsDaily;
import com.atelier.media.model.AnalyticsEvent;
import com.atelier.media.model.Media;
import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.AnalyticsDailyRepository;
import com.atelier.media.repository.AnalyticsEventRepository;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final AnalyticsEventRepository eventRepository = mock(AnalyticsEventRepository.class);
    private final AnalyticsDailyRepository dailyRepository = mock(AnalyticsDailyRepository.class);
    private final TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));

    private AnalyticsService service;
    private Restaurant restaurant;
    private Media ownedDish;
    private Media foreignDish;

    @BeforeEach
    void setUp() {
        // Use synchronous execution for deterministic unit tests
        service = new AnalyticsService(restaurantRepository, mediaRepository, eventRepository, dailyRepository, tx, Runnable::run);

        restaurant = TestFixtures.restaurant(10L, "bistro-paris");
        when(restaurantRepository.findBySlug("bistro-paris")).thenReturn(Optional.of(restaurant));

        ownedDish = Media.builder().id(101L).restaurantId(10L).dishTitle("Croissant").build();
        foreignDish = Media.builder().id(202L).restaurantId(99L).dishTitle("Pasta").build();

        when(mediaRepository.findById(101L)).thenReturn(Optional.of(ownedDish));
        when(mediaRepository.findById(202L)).thenReturn(Optional.of(foreignDish));
    }

    @Test
    void invalidEventTypeThrows400InvalidFieldException() {
        AnalyticsEventDto dto = new AnalyticsEventDto("bistro-paris", null, "INVALID_TYPE_XYZ");
        assertThatThrownBy(() -> service.recordEvent(dto, "1.2.3.4"))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("Invalid eventType");

        verify(eventRepository, never()).save(any());
    }

    @Test
    void dishBelongingToAnotherRestaurantThrows400InvalidFieldException() {
        AnalyticsEventDto dto = new AnalyticsEventDto("bistro-paris", 202L, "MODEL_3D_VIEW");
        assertThatThrownBy(() -> service.recordEvent(dto, "1.2.3.4"))
                .isInstanceOf(InvalidFieldException.class)
                .hasMessageContaining("does not belong to restaurant");

        verify(eventRepository, never()).save(any());
    }

    @Test
    void deduplicatedWithin30sWritesOnlyOnce() {
        AnalyticsEventDto dto = new AnalyticsEventDto("bistro-paris", 101L, "MODEL_3D_VIEW");

        // First call
        service.recordEvent(dto, "10.0.0.5");
        // Immediate second call with identical IP, dish, type, restaurant
        service.recordEvent(dto, "10.0.0.5");

        // Event should only have been persisted once
        verify(eventRepository, times(1)).save(any(AnalyticsEvent.class));
        verify(restaurantRepository, times(1)).incrementModelViews(10L);
        verify(mediaRepository, times(1)).incrementModelViews(101L);
    }

    @Test
    void rateLimitedAfter60EventsPerMinute() {
        String clientIp = "192.168.1.88";

        // 60 requests should pass (different dishes to avoid deduplication)
        for (int i = 0; i < 60; i++) {
            long dishId = 1000L + i;
            Media mockDish = Media.builder().id(dishId).restaurantId(10L).build();
            when(mediaRepository.findById(dishId)).thenReturn(Optional.of(mockDish));

            AnalyticsEventDto dto = new AnalyticsEventDto("bistro-paris", dishId, "MENU_VIEW");
            service.recordEvent(dto, clientIp);
        }

        // 61st request should be rate-limited
        AnalyticsEventDto overflowDto = new AnalyticsEventDto("bistro-paris", 2000L, "MENU_VIEW");
        assertThatThrownBy(() -> service.recordEvent(overflowDto, clientIp))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void validEventUpdatesCountersAndDailyAggregate() {
        AnalyticsEventDto dto = new AnalyticsEventDto("bistro-paris", 101L, "AR_LAUNCH");
        service.recordEvent(dto, "1.2.3.4");

        verify(restaurantRepository).incrementArLaunches(10L);
        verify(mediaRepository).incrementArLaunches(101L);
        verify(eventRepository).save(any(AnalyticsEvent.class));
        verify(dailyRepository).save(any(AnalyticsDaily.class));
    }
}
