package com.infy.catalog.service;

/**
 * Page-size rules: a missing or zero size uses {@code defaultSize}; anything above
 * {@code maxSize} is capped so one request can never pull the whole table.
 */
public record PagingPolicy(int defaultSize, int maxSize) {

    public PagingPolicy {
        if (defaultSize < 1 || maxSize < defaultSize) {
            throw new IllegalArgumentException("Require 1 <= defaultSize <= maxSize");
        }
    }

    int resolveSize(Integer requested) {
        if (requested == null || requested == 0) {
            return defaultSize;
        }
        return Math.min(requested, maxSize);
    }
}
