package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.config.CacheConfig;
import com.atelier.media.dto.CategoryDto;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.PublicMenuDto;
import com.atelier.media.dto.RestaurantBrandingDto;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.model.Category;
import com.atelier.media.model.Media;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.repository.CategoryRepository;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.RestaurantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Guest read path: branding + custom categories + in-stock dishes, cached per restaurant slug. */
@Slf4j
@Service
public class PublicMenuService {
    private GuestExperienceService experience;
    @org.springframework.beans.factory.annotation.Autowired public void setGuestExperience(GuestExperienceService service){experience=service;}
    private EntitlementService entitlements;
    @org.springframework.beans.factory.annotation.Autowired public void setEntitlements(EntitlementService service){entitlements=service;}


    private final RestaurantRepository restaurantRepository;
    private final MediaRepository mediaRepository;
    private final CategoryRepository categoryRepository;
    private final AtelierProperties props;

    public PublicMenuService(RestaurantRepository restaurantRepository,
                             MediaRepository mediaRepository,
                             AtelierProperties props) {
        this(restaurantRepository, mediaRepository, null, props);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PublicMenuService(RestaurantRepository restaurantRepository,
                             MediaRepository mediaRepository,
                             CategoryRepository categoryRepository,
                             AtelierProperties props) {
        this.restaurantRepository = restaurantRepository;
        this.mediaRepository = mediaRepository;
        this.categoryRepository = categoryRepository;
        this.props = props;
    }

    /** Unknown, suspended or deleted restaurants answer 404. Respects isMenuLive master switch. */
    public PublicMenuDto getMenu(String slug) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .filter(Restaurant::isPubliclyVisible)
                .orElseThrow(() -> new RestaurantNotFoundException(slug));

        List<Category> rawCategories = categoryRepository != null
                ? categoryRepository.findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(restaurant.getId())
                : List.of();
        Set<String> seenCatNames = new HashSet<>();
        List<Category> categories = new ArrayList<>();
        for (Category c : rawCategories) {
            if (c.getName() != null && seenCatNames.add(c.getName().trim().toLowerCase(Locale.ROOT))) {
                categories.add(c);
            }
        }
        List<CategoryDto> categoryDtos = categories.stream().map(CategoryDto::from).toList();
        Map<Long, String> categoryNames = rawCategories.stream().collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        boolean live = !Boolean.FALSE.equals(restaurant.getIsMenuLive());

        // When private, outside guests receive the branding holding card with 0 items
        if (!live) {
            log.info("Menu /r/{} is marked private (isMenuLive=false). Serving luxury holding response.", slug);
            return new PublicMenuDto(RestaurantBrandingDto.from(restaurant), List.of(), categoryDtos, false);
        }

        List<Media> rawItems = mediaRepository
                .findByRestaurantIdAndUploadStatusAndAvailableTrueOrderByCreatedAtDesc(
                        restaurant.getId(), UploadStatus.COMPLETED, PageRequest.of(0, props.menuLimit()));

        List<MediaResponseDto> items = rawItems.stream().map(m -> {
            String catName = m.getCategoryId() != null ? categoryNames.get(m.getCategoryId()) : null;
            return DishMapper.toGuestDto(m, catName);
        }).toList();

        log.debug("Menu cache miss for {}: {} dish(es)", slug, items.size());
        List<String> modes=entitlements==null?List.of("SIMPLE","THREE_D","AR"):entitlements.allowedModes(restaurant.getId()).stream().map(Object::toString).toList();
        String mode=experience==null?(modes.contains("AR")?"AR":modes.contains("THREE_D")?"THREE_D":"SIMPLE"):experience.selectedMode(restaurant.getId(),modes);
        items=items.stream().map(item->item.restrictModes(!"SIMPLE".equals(mode),"AR".equals(mode))).toList();
        return new PublicMenuDto(RestaurantBrandingDto.from(restaurant), items, categoryDtos, true,modes,mode);
    }

    /** Authenticated staff preview bypasses the live/private filter. */
    public PublicMenuDto getStaffPreview(String slug) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new RestaurantNotFoundException(slug));
        return buildStaffPreview(restaurant);
    }

    public PublicMenuDto getStaffPreview(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
        return buildStaffPreview(restaurant);
    }

    private PublicMenuDto buildStaffPreview(Restaurant restaurant) {
        List<Category> rawCategories = categoryRepository != null
                ? categoryRepository.findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(restaurant.getId())
                : List.of();
        Set<String> seenCatNames = new HashSet<>();
        List<Category> categories = new ArrayList<>();
        for (Category c : rawCategories) {
            if (c.getName() != null && seenCatNames.add(c.getName().trim().toLowerCase(Locale.ROOT))) {
                categories.add(c);
            }
        }
        List<CategoryDto> categoryDtos = categories.stream().map(CategoryDto::from).toList();
        Map<Long, String> categoryNames = rawCategories.stream().collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));

        List<Media> rawItems = mediaRepository
                .findByRestaurantIdAndUploadStatusAndAvailableTrueOrderByCreatedAtDesc(
                        restaurant.getId(), UploadStatus.COMPLETED, PageRequest.of(0, props.menuLimit()));

        List<MediaResponseDto> items = rawItems.stream().map(m -> {
            String catName = m.getCategoryId() != null ? categoryNames.get(m.getCategoryId()) : null;
            return DishMapper.toDto(m, catName);
        }).toList();

        List<String> modes=entitlements==null?List.of("SIMPLE","THREE_D","AR"):entitlements.allowedModes(restaurant.getId()).stream().map(Object::toString).toList();
        String mode=experience==null?"AR":experience.selectedMode(restaurant.getId(),modes);
        items=items.stream().map(item->item.restrictModes(!"SIMPLE".equals(mode),"AR".equals(mode))).toList();
        return new PublicMenuDto(RestaurantBrandingDto.from(restaurant), items, categoryDtos, true,modes,mode);
    }
}
