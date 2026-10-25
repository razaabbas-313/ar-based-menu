package com.atelier.media.service;

import com.atelier.media.dto.CategoryCreateDto;
import com.atelier.media.dto.CategoryDto;
import com.atelier.media.dto.CategoryUpdateDto;
import com.atelier.media.exception.ConflictException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.model.Category;
import com.atelier.media.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public List<CategoryDto> getCategories(Long restaurantId) {
        seedDefaultsIfEmpty(restaurantId);
        List<Category> all = categoryRepository.findByRestaurantIdOrderByDisplayOrderAscIdAsc(restaurantId);
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<CategoryDto> out = new java.util.ArrayList<>();
        for (Category c : all) {
            if (c.getName() != null && seen.add(c.getName().trim().toLowerCase(java.util.Locale.ROOT))) {
                out.add(CategoryDto.from(c));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getActiveCategories(Long restaurantId) {
        List<Category> all = categoryRepository.findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAscIdAsc(restaurantId);
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<CategoryDto> out = new java.util.ArrayList<>();
        for (Category c : all) {
            if (c.getName() != null && seen.add(c.getName().trim().toLowerCase(java.util.Locale.ROOT))) {
                out.add(CategoryDto.from(c));
            }
        }
        return out;
    }

    @Transactional
    public CategoryDto createCategory(Long restaurantId, CategoryCreateDto dto) {
        String name = dto.name().strip();
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw new ConflictException("name", "A category with the name '" + name + "' already exists.");
        }
        int order = dto.displayOrder() != null ? dto.displayOrder() : (int) categoryRepository.countByRestaurantId(restaurantId) + 1;
        Category category = Category.builder()
                .restaurantId(restaurantId)
                .name(name)
                .displayOrder(order)
                .isActive(true)
                .build();
        return CategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto updateCategory(Long restaurantId, Long categoryId, CategoryUpdateDto dto) {
        Category category = categoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new InvalidFieldException("id", "Category not found"));
        String name = dto.name().strip();
        if (!category.getName().equalsIgnoreCase(name) && categoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw new ConflictException("name", "A category with the name '" + name + "' already exists.");
        }
        category.setName(name);
        if (dto.displayOrder() != null) {
            category.setDisplayOrder(dto.displayOrder());
        }
        if (dto.isActive() != null) {
            category.setIsActive(dto.isActive());
        }
        return CategoryDto.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long restaurantId, Long categoryId) {
        Category category = categoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new InvalidFieldException("id", "Category not found"));
        categoryRepository.delete(category);
    }

    @Transactional
    public List<CategoryDto> reorderCategories(Long restaurantId, List<Long> orderedIds) {
        List<Category> all = categoryRepository.findByRestaurantIdOrderByDisplayOrderAscIdAsc(restaurantId);
        for (int i = 0; i < orderedIds.size(); i++) {
            Long targetId = orderedIds.get(i);
            for (Category c : all) {
                if (c.getId().equals(targetId)) {
                    c.setDisplayOrder(i + 1);
                    break;
                }
            }
        }
        categoryRepository.saveAll(all);
        return all.stream().map(CategoryDto::from).toList();
    }

    @Transactional
    public void seedDefaultsIfEmpty(Long restaurantId) {
        if (categoryRepository.countByRestaurantId(restaurantId) == 0) {
            List<String> defaultNames = List.of("Starters", "Mains", "Desserts", "Chef Specials", "Drinks");
            for (int i = 0; i < defaultNames.size(); i++) {
                Category cat = Category.builder()
                        .restaurantId(restaurantId)
                        .name(defaultNames.get(i))
                        .displayOrder(i + 1)
                        .isActive(true)
                        .build();
                categoryRepository.save(cat);
            }
        }
    }
}
