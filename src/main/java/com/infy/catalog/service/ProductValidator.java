package com.infy.catalog.service;

import com.infy.catalog.domain.ProductData;
import com.infy.catalog.domain.exception.InvalidRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates and normalizes product input. Every string is bounded; all violations are
 * reported together. Limits match the column sizes in V1__create_product_table.sql.
 */
final class ProductValidator {

    static final int SKU_MAX = 32;
    static final int NAME_MAX = 120;
    static final int DESCRIPTION_MAX = 1000;
    static final int CATEGORY_MAX = 60;
    static final int KEYWORD_MAX = 100;
    static final BigDecimal PRICE_MAX = new BigDecimal("10000000");
    static final int STOCK_MAX = 1_000_000;

    private static final Pattern SKU_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9-]{2," + (SKU_MAX - 1) + "}");

    private ProductValidator() {
    }

    /** Returns trimmed, upper-cased-SKU, 2-decimal-price data, or throws with all violations. */
    static ProductData validated(ProductData input) {
        if (input == null) {
            throw new InvalidRequestException(List.of("product is required"));
        }
        String sku = input.sku() == null ? null : input.sku().trim().toUpperCase(Locale.ROOT);
        String name = trimToNull(input.name());
        String description = trimToNull(input.description());
        String category = trimToNull(input.category());
        BigDecimal price = input.price();
        Integer stock = input.stock();

        List<String> errors = new ArrayList<>();
        if (sku == null || sku.isEmpty()) {
            errors.add("sku is required");
        } else if (!SKU_PATTERN.matcher(sku).matches()) {
            errors.add("sku must be 3-" + SKU_MAX + " characters of A-Z, 0-9 or '-', starting with a letter or digit");
        }
        if (name == null) {
            errors.add("name is required");
        } else if (name.length() > NAME_MAX) {
            errors.add("name must be at most " + NAME_MAX + " characters");
        }
        if (description != null && description.length() > DESCRIPTION_MAX) {
            errors.add("description must be at most " + DESCRIPTION_MAX + " characters");
        }
        if (category != null && category.length() > CATEGORY_MAX) {
            errors.add("category must be at most " + CATEGORY_MAX + " characters");
        }
        if (price == null) {
            errors.add("price is required");
        } else {
            if (price.signum() < 0) {
                errors.add("price must be >= 0");
            }
            if (price.compareTo(PRICE_MAX) > 0) {
                errors.add("price must be <= " + PRICE_MAX.toPlainString());
            }
            if (price.stripTrailingZeros().scale() > 2) {
                errors.add("price must have at most 2 decimal places");
            }
        }
        if (stock == null) {
            errors.add("stock is required");
        } else if (stock < 0 || stock > STOCK_MAX) {
            errors.add("stock must be between 0 and " + STOCK_MAX);
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return new ProductData(sku, name, description, category, price.setScale(2, RoundingMode.UNNECESSARY), stock);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
