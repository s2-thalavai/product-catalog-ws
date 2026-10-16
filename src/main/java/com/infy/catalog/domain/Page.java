package com.infy.catalog.domain;

import java.util.List;
import java.util.function.Function;

/** One page of results plus the numbers a client needs to navigate. */
public record Page<T>(List<T> items, int page, int size, long totalElements) {

    public Page {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = items.stream().<R>map(mapper).toList();
        return new Page<>(mapped, page, size, totalElements);
    }
}
