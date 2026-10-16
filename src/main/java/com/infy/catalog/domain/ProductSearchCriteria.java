package com.infy.catalog.domain;

import java.math.BigDecimal;

/**
 * Optional filters for a product search. Null means "no filter".
 *
 * @param keyword     case-insensitive substring match on name, SKU or description
 * @param category    case-insensitive exact match
 * @param minPrice    inclusive lower bound
 * @param maxPrice    inclusive upper bound
 * @param inStockOnly when true, only products with stock > 0
 */
public record ProductSearchCriteria(
        String keyword,
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStockOnly) {

    public static ProductSearchCriteria empty() {
        return new ProductSearchCriteria(null, null, null, null, false);
    }

    /** Trims text filters and turns blank values into null. */
    public ProductSearchCriteria normalized() {
        return new ProductSearchCriteria(trimToNull(keyword), trimToNull(category), minPrice, maxPrice, inStockOnly);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
