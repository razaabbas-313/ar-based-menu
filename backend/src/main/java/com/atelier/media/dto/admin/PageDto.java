package com.atelier.media.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Stable JSON shape for paginated results (Spring's PageImpl JSON is not a stable contract). */
public record PageDto<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    @JsonProperty("content")
    public List<T> content() {
        return items;
    }

    public static <E, T> PageDto<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageDto<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageDto<T> of(Page<T> page) {
        return new PageDto<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageDto<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageDto<>(items, page, size, totalElements, totalPages);
    }
}
