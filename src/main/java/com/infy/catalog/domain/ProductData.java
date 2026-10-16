package com.infy.catalog.domain;

import java.math.BigDecimal;

/**
 * The writable fields of a product, used for create and full update.
 * Values arrive raw from the client; {@code ProductService} validates and normalizes them.
 */
public record ProductData(
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        Integer stock) {
}
