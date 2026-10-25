package com.atelier.media.service;

import com.atelier.media.dto.PublicMenuDto;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicMenuServiceTest {

    private final RestaurantRepository restaurants = mock(RestaurantRepository.class);
    private final MediaRepository media = mock(MediaRepository.class);
    private final PublicMenuService service = new PublicMenuService(restaurants, media, TestFixtures.atelierProperties());

    @Test
    void returnsBrandingAndOnlyInStockCompletedDishes() {
        Restaurant r = TestFixtures.restaurant(1L, "bistro-a");
        r.setThemeAccent("gold");
        r.setInstagramHandle("bistro");
        when(restaurants.findBySlug("bistro-a")).thenReturn(Optional.of(r));
        when(media.findByRestaurantIdAndUploadStatusAndAvailableTrueOrderByCreatedAtDesc(eq(1L), eq(UploadStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(List.of(TestFixtures.mediaWithModel(5L, 1L, "bistro-a")));

        PublicMenuDto menu = service.getMenu("bistro-a");

        assertThat(menu.restaurant().themeAccent()).isEqualTo("gold");
        assertThat(menu.restaurant().instagramHandle()).isEqualTo("bistro");
        assertThat(menu.items()).hasSize(1);
        assertThat(menu.items().get(0).is3d()).isTrue();
        assertThat(menu.items().get(0).imageUrl()).endsWith(".webp");
        assertThat(menu.items().get(0).dishCode()).isNull(); // strictly hidden from guests
    }

    @Test
    void suspendedPendingAndDeletedRestaurantsAreNotPublic() {
        Restaurant suspended = TestFixtures.restaurant(1L, "a-suspended");
        suspended.setStatus(RestaurantStatus.SUSPENDED);
        Restaurant pending = TestFixtures.restaurant(2L, "b-pending");
        pending.setStatus(RestaurantStatus.PENDING);
        Restaurant deleted = TestFixtures.restaurant(3L, "c-deleted");
        deleted.setDeletedAt(Instant.now());
        when(restaurants.findBySlug("a-suspended")).thenReturn(Optional.of(suspended));
        when(restaurants.findBySlug("b-pending")).thenReturn(Optional.of(pending));
        when(restaurants.findBySlug("c-deleted")).thenReturn(Optional.of(deleted));

        for (String slug : List.of("a-suspended", "b-pending", "c-deleted", "unknown")) {
            assertThatThrownBy(() -> service.getMenu(slug)).isInstanceOf(RestaurantNotFoundException.class);
        }
    }
}
