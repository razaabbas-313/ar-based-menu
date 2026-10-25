package com.atelier.media.service;

import com.atelier.media.dto.AnalyticsEventDto;
import com.atelier.media.dto.StudioAnalyticsDto;
import com.atelier.media.dto.TopDishDto;
import com.atelier.media.dto.admin.AdminRestaurantDto;
import com.atelier.media.dto.admin.PlatformAnalyticsDto;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.exception.TooManyAttemptsException;
import com.atelier.media.model.AnalyticsDaily;
import com.atelier.media.model.AnalyticsDailyId;
import com.atelier.media.model.AnalyticsEvent;
import com.atelier.media.model.AnalyticsEventType;
import com.atelier.media.model.Media;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.repository.AnalyticsDailyRepository;
import com.atelier.media.repository.AnalyticsEventRepository;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AnalyticsService {

    private final RestaurantRepository restaurantRepository;
    private final MediaRepository mediaRepository;
    private final AnalyticsEventRepository eventRepository;
    private final AnalyticsDailyRepository dailyRepository;
    private final TransactionTemplate tx;
    private final Executor asyncExecutor;

    private final Cache<String, AtomicInteger> ipRateLimit;
    private final Cache<String, Boolean> recentViews;

    @org.springframework.beans.factory.annotation.Autowired
    public AnalyticsService(RestaurantRepository restaurantRepository,
                            MediaRepository mediaRepository,
                            AnalyticsEventRepository eventRepository,
                            AnalyticsDailyRepository dailyRepository,
                            TransactionTemplate tx) {
        this(restaurantRepository, mediaRepository, eventRepository, dailyRepository, tx, Executors.newVirtualThreadPerTaskExecutor());
    }

    public AnalyticsService(RestaurantRepository restaurantRepository,
                            MediaRepository mediaRepository,
                            AnalyticsEventRepository eventRepository,
                            AnalyticsDailyRepository dailyRepository,
                            TransactionTemplate tx,
                            Executor asyncExecutor) {
        this.restaurantRepository = restaurantRepository;
        this.mediaRepository = mediaRepository;
        this.eventRepository = eventRepository;
        this.dailyRepository = dailyRepository;
        this.tx = tx;
        this.asyncExecutor = asyncExecutor;

        this.ipRateLimit = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(50_000)
                .build();

        this.recentViews = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(100_000)
                .build();
    }

    public void recordEvent(AnalyticsEventDto dto) {
        recordEvent(dto, "unknown");
    }

    /**
     * Guest interaction tracking: validated synchronously on request thread,
     * rate-limited and deduplicated, then DB write is dispatched asynchronously.
     */
    public void recordEvent(AnalyticsEventDto dto, String clientIp) {
        if (dto.slug() == null || dto.slug().isBlank()) {
            throw new InvalidFieldException("slug", "Slug is required");
        }

        // 1. Validate eventType allowlist (MENU_VIEW, MODEL_3D_VIEW, AR_LAUNCH) -> 400 if invalid
        AnalyticsEventType eventType = AnalyticsEventType.parse(dto.eventType());

        // 2. Rate-limit per IP: cap at 60 events/minute -> 429 if exceeded (protects DB from DoS)
        String safeIp = clientIp != null && !clientIp.isBlank() ? clientIp.trim() : "unknown";
        if (!"unknown".equalsIgnoreCase(safeIp)) {
            AtomicInteger count = ipRateLimit.get(safeIp, k -> new AtomicInteger());
            if (count.incrementAndGet() > 60) {
                log.warn("Analytics rate limit exceeded for IP: {}", safeIp);
                throw new TooManyAttemptsException(Duration.ofMinutes(1));
            }
        }

        // 3. Resolve restaurant -> 404 if not found
        Restaurant restaurant = restaurantRepository.findBySlug(dto.slug().strip())
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new RestaurantNotFoundException(dto.slug()));

        // 4. Verify dish ownership if dishId provided -> 400 if foreign or missing
        if (dto.dishId() != null) {
            Media dish = mediaRepository.findById(dto.dishId())
                    .orElseThrow(() -> new InvalidFieldException("dishId", "Dish not found: " + dto.dishId()));
            if (!dish.getRestaurantId().equals(restaurant.getId())) {
                throw new InvalidFieldException("dishId", "Dish does not belong to restaurant: " + dto.slug());
            }
        }

        // 5. De-duplicate rapid duplicate views within 30 seconds -> ignore duplicate silently
        String dedupKey = safeIp + "|" + (dto.dishId() == null ? 0 : dto.dishId()) + "|" + eventType.name() + "|" + restaurant.getId();
        if (recentViews.getIfPresent(dedupKey) != null) {
            log.debug("Analytics event deduplicated: {}", dedupKey);
            return;
        }
        recentViews.put(dedupKey, Boolean.TRUE);

        // 6. Asynchronous DB write off the request thread
        Long restaurantId = restaurant.getId();
        Long dishId = dto.dishId();
        CompletableFuture.runAsync(() -> persistEvent(restaurantId, dishId, eventType), asyncExecutor);
    }

    private void persistEvent(Long restaurantId, Long dishId, AnalyticsEventType type) {
        tx.executeWithoutResult(status -> {
            try {
                // Increment counters
                switch (type) {
                    case MENU_VIEW -> {
                        restaurantRepository.incrementMenuViews(restaurantId);
                        if (dishId != null) mediaRepository.incrementViews(dishId);
                    }
                    case MODEL_3D_VIEW -> {
                        restaurantRepository.incrementModelViews(restaurantId);
                        if (dishId != null) mediaRepository.incrementModelViews(dishId);
                    }
                    case AR_LAUNCH -> {
                        restaurantRepository.incrementArLaunches(restaurantId);
                        if (dishId != null) mediaRepository.incrementArLaunches(dishId);
                    }
                }

                // Save event
                AnalyticsEvent event = AnalyticsEvent.builder()
                        .restaurantId(restaurantId)
                .dishId(dishId)
                .eventType(type.name())
                .build();
                eventRepository.save(event);

                // Update daily aggregate table
                LocalDate today = LocalDate.now(ZoneOffset.UTC);
                Long safeDishId = dishId != null ? dishId : 0L;
                AnalyticsDailyId dailyId = new AnalyticsDailyId(restaurantId, safeDishId, type.name(), today);
                Optional<AnalyticsDaily> existingDaily = dailyRepository.findById(dailyId);
                if (existingDaily.isPresent()) {
                    AnalyticsDaily daily = existingDaily.get();
                    daily.setEventCount(daily.getEventCount() + 1);
                    dailyRepository.save(daily);
                } else {
                    dailyRepository.save(new AnalyticsDaily(restaurantId, safeDishId, type.name(), today, 1L));
                }
            } catch (Exception e) {
                log.error("Failed to persist analytics event for restaurant {}: {}", restaurantId, e.getMessage(), e);
            }
        });
    }

    /** Restaurant owner analytics dashboard. */
    @Transactional(readOnly = true)
    public StudioAnalyticsDto getStudioAnalytics(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
        if (restaurant == null) {
            return new StudioAnalyticsDto(0, 0, 0, 0.0, List.of());
        }

        long views = restaurant.getMenuViewsCount() != null ? restaurant.getMenuViewsCount() : 0L;
        long models = restaurant.getModelViewsCount() != null ? restaurant.getModelViewsCount() : 0L;
        long ar = restaurant.getArLaunchesCount() != null ? restaurant.getArLaunchesCount() : 0L;

        double rate = views > 0 ? ((double) models / views) * 100.0 : 0.0;
        rate = Math.round(rate * 10.0) / 10.0;

        List<Media> topDishes = mediaRepository.findTop5ByRestaurantIdAndUploadStatusOrderByViewsCountDesc(
                restaurantId, UploadStatus.COMPLETED);

        List<TopDishDto> dishDtos = topDishes.stream().map(d -> new TopDishDto(
                d.getId(),
                d.getDishTitle(),
                d.getS3Url(),
                d.getViewsCount() != null ? d.getViewsCount() : 0L,
                d.getModelViewsCount() != null ? d.getModelViewsCount() : 0L,
                d.getArLaunchesCount() != null ? d.getArLaunchesCount() : 0L
        )).toList();

        return new StudioAnalyticsDto(views, models, ar, rate, dishDtos);
    }

    /** Platform-wide super admin analytics. */
    @Transactional(readOnly = true)
    public PlatformAnalyticsDto getPlatformAnalytics() {
        long total = restaurantRepository.countByDeletedAtIsNull();
        long active = restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.ACTIVE);
        long trial = restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.TRIAL);
        long pending = restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.PENDING)
                + restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.PENDING_ONBOARDING);
        long suspended = restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.SUSPENDED);

        Number totalStorage = restaurantRepository.totalStorageBytes();
        Number totalDishes = restaurantRepository.totalDishes();
        Number totalModels = restaurantRepository.totalModels();

        long modelsInProd = mediaRepository.countByModelRequestStatus("PENDING");
        long modelsPublished = totalModels != null ? totalModels.longValue() : 0L;
        long totalAr = eventRepository.countByEventType("AR_LAUNCH");
        long totalViews = eventRepository.countByEventType("MENU_VIEW");

        List<AdminRestaurantDto> topByStorage = restaurantRepository.findTop5ByDeletedAtIsNullOrderByStorageUsedBytesDesc()
                .stream().map(AdminRestaurantDto::from).toList();
        List<AdminRestaurantDto> topByDishes = restaurantRepository.findTop5ByDeletedAtIsNullOrderByDishCountDesc()
                .stream().map(AdminRestaurantDto::from).toList();

        return new PlatformAnalyticsDto(
                total,
                active,
                trial,
                pending,
                suspended,
                totalDishes != null ? totalDishes.longValue() : 0L,
                modelsPublished,
                totalStorage != null ? totalStorage.longValue() : 0L,
                topByStorage,
                topByDishes,
                Instant.now(),
                modelsInProd,
                modelsPublished,
                totalAr,
                totalViews
        );
    }
}
