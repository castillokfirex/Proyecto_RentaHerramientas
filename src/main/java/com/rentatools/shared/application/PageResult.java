package com.rentatools.shared.application;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resultado paginado independiente del framework.
 */
public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public PageResult {
        Objects.requireNonNull(items, "items must not be null");
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResult<>(items, page, size, totalElements, totalPages);
    }

    public <R> PageResult<R> map(Function<? super T, R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        List<R> mapped = items.stream().map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalElements, totalPages);
    }
}
