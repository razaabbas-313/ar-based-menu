package com.atelier.media.support;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.config.CacheConfig;
import com.atelier.media.repository.CategoryRepository;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.service.AnalyticsService;
import com.atelier.media.service.MenuCache;
import com.atelier.media.service.RestaurantAccess;
import com.atelier.media.service.S3Storage;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wiring shared by service tests: Mockito repositories and S3 client, a REAL S3 presigner with
 * dummy keys (SigV4 signing is local; nothing is sent to AWS), a TransactionTemplate over a mock
 * transaction manager (so tests can assert commit-before-S3 ordering) and a real in-memory cache.
 * Restaurant A = id 1 "bistro-a", restaurant B = id 2 "bistro-b".
 */
public class ServiceHarness implements AutoCloseable {

    public static final long A = 1L;
    public static final long B = 2L;

    public final MediaRepository mediaRepository = mock(MediaRepository.class);
    public final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    public final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    public final AnalyticsService analyticsService = mock(AnalyticsService.class);
    public final S3Client s3Client = mock(S3Client.class);
    public final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    public final TransactionTemplate tx = new TransactionTemplate(txManager);
    public final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(CacheConfig.MENU_CACHE);
    public final MenuCache menuCache = new MenuCache(cacheManager);
    public final AtelierProperties props = TestFixtures.atelierProperties();
    public final S3Presigner presigner = S3Presigner.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")))
            .build();
    public final S3Storage storage = new S3Storage(presigner, s3Client, TestFixtures.awsProperties(), props);
    public final RestaurantAccess restaurantAccess = new RestaurantAccess(restaurantRepository);

    public ServiceHarness() {
        when(restaurantRepository.findById(A)).thenReturn(Optional.of(TestFixtures.restaurant(A, "bistro-a")));
        when(restaurantRepository.findById(B)).thenReturn(Optional.of(TestFixtures.restaurant(B, "bistro-b")));
        when(restaurantRepository.findByIdForUpdate(A)).thenAnswer(inv -> restaurantRepository.findById(A));
        when(restaurantRepository.findByIdForUpdate(B)).thenAnswer(inv -> restaurantRepository.findById(B));
        when(mediaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        menu().put("bistro-a", "cached menu A");
        menu().put("bistro-b", "cached menu B");
    }

    public Cache menu() {
        return cacheManager.getCache(CacheConfig.MENU_CACHE);
    }

    public boolean cached(String slug) {
        return menu().get(slug) != null;
    }

    @Override
    public void close() {
        presigner.close();
    }
}
