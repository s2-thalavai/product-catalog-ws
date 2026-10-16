package com.infy.catalog.domain;

import java.util.Objects;

/**
 * A validated page request (0-based page index). Build it through {@code ProductService},
 * which applies defaults and the page-size cap.
 */
public record PageRequest(int page, int size, SortField sort, SortDirection direction) {

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(direction, "direction");
    }

    public long offset() {
        return (long) page * size;
    }
}
