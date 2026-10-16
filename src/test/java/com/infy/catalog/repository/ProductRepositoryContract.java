package com.infy.catalog.repository;

import com.infy.catalog.domain.Page;
import com.infy.catalog.domain.PageRequest;
import com.infy.catalog.domain.Product;
import com.infy.catalog.domain.ProductData;
import com.infy.catalog.domain.ProductSearchCriteria;
import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.SortField;
import com.infy.catalog.domain.exception.DuplicateSkuException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour every {@link ProductRepository} must have. Run against the in-memory store
 * (unit test) and PostgreSQL (Testcontainers IT), so both stay interchangeable.
 */
abstract class ProductRepositoryContract {

    protected ProductRepository repository;

    /** Must return a repository backed by an empty store. */
    protected abstract ProductRepository createRepository();

    @BeforeEach
    void setUpRepository() {
        repository = createRepository();
    }

    @Test
    void insertAssignsIdAndTimestamps() {
        Product product = repository.insert(data("SKU-1", "Alpha", "Cat", "10.00", 1));

        assertNotNull(product.id());
        assertNotNull(product.createdAt());
        assertEquals(product.createdAt(), product.updatedAt());
        assertEquals(product, repository.findById(product.id()).orElseThrow());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertTrue(repository.findById(999_999).isEmpty());
    }

    @Test
    void insertRejectsDuplicateSku() {
        repository.insert(data("SKU-DUP", "Alpha", "Cat", "10.00", 1));

        assertThrows(DuplicateSkuException.class, () -> repository.insert(data("SKU-DUP", "Bravo", "Cat", "10.00", 1)));
    }

    @Test
    void updateReplacesFieldsAndKeepsCreatedAt() {
        Product original = repository.insert(data("SKU-U", "Alpha", "Cat", "10.00", 1));

        Product updated = repository.update(original.id(), data("SKU-U2", "Bravo", "Other", "20.00", 7)).orElseThrow();

        assertEquals("SKU-U2", updated.sku());
        assertEquals("Bravo", updated.name());
        assertEquals(0, new BigDecimal("20.00").compareTo(updated.price()));
        assertEquals(7, updated.stock());
        assertEquals(original.createdAt(), updated.createdAt());
        assertFalse(updated.updatedAt().isBefore(original.updatedAt()));
    }

    @Test
    void updateReturnsEmptyForUnknownId() {
        assertTrue(repository.update(999_999, data("SKU-X", "Alpha", "Cat", "1.00", 1)).isEmpty());
    }

    @Test
    void updateRejectsSkuOwnedByAnotherProduct() {
        repository.insert(data("SKU-TAKEN", "Alpha", "Cat", "1.00", 1));
        Product other = repository.insert(data("SKU-FREE", "Bravo", "Cat", "1.00", 1));

        assertThrows(DuplicateSkuException.class,
                () -> repository.update(other.id(), data("SKU-TAKEN", "Bravo", "Cat", "1.00", 1)));
    }

    @Test
    void deleteRemovesProductOnce() {
        Product product = repository.insert(data("SKU-D", "Alpha", "Cat", "1.00", 1));

        assertTrue(repository.deleteById(product.id()));
        assertFalse(repository.deleteById(product.id()));
        assertTrue(repository.findById(product.id()).isEmpty());
    }

    @Test
    void keywordMatchesNameSkuOrDescriptionCaseInsensitively() {
        repository.insert(new ProductData("SKU-K1", "Wireless Mouse", null, "Cat", new BigDecimal("1.00"), 1));
        repository.insert(new ProductData("MOUSE-K2", "Pointer", null, "Cat", new BigDecimal("1.00"), 1));
        repository.insert(new ProductData("SKU-K3", "Pad", "Works with any mouse", "Cat", new BigDecimal("1.00"), 1));
        repository.insert(new ProductData("SKU-K4", "Keyboard", null, "Cat", new BigDecimal("1.00"), 1));

        Page<Product> page = repository.search(criteria("MoUsE", null, null, null, false), request(0, 10));

        assertEquals(List.of("SKU-K1", "MOUSE-K2", "SKU-K3"), skus(page));
    }

    @Test
    void keywordTreatsLikeWildcardsLiterally() {
        repository.insert(data("SKU-W1", "50% Off Mug", "Cat", "1.00", 1));
        repository.insert(data("SKU-W2", "500 Mug", "Cat", "1.00", 1));
        repository.insert(data("SKU-W3", "Big_Mug", "Cat", "1.00", 1));
        repository.insert(data("SKU-W4", "BigXMug", "Cat", "1.00", 1));

        assertEquals(List.of("SKU-W1"), skus(repository.search(criteria("50%", null, null, null, false), request(0, 10))));
        assertEquals(List.of("SKU-W3"), skus(repository.search(criteria("g_m", null, null, null, false), request(0, 10))));
    }

    @Test
    void filtersByCategoryPriceRangeAndStock() {
        repository.insert(data("SKU-F1", "Alpha", "Books", "100.00", 5));
        repository.insert(data("SKU-F2", "Bravo", "books", "200.00", 0));
        repository.insert(data("SKU-F3", "Charlie", "Books", "300.00", 5));
        repository.insert(data("SKU-F4", "Delta", "Office", "200.00", 5));

        Page<Product> page = repository.search(
                criteria(null, "BOOKS", new BigDecimal("150"), new BigDecimal("300.00"), true), request(0, 10));

        assertEquals(List.of("SKU-F3"), skus(page));
        assertEquals(1, page.totalElements());
    }

    @Test
    void paginatesWithTotalsAndStableOrder() {
        for (int i = 1; i <= 7; i++) {
            repository.insert(data("SKU-P" + i, "Item " + i, "Cat", "10.00", 1));
        }

        Page<Product> second = repository.search(ProductSearchCriteria.empty(), request(1, 3));
        Page<Product> third = repository.search(ProductSearchCriteria.empty(), request(2, 3));
        Page<Product> beyond = repository.search(ProductSearchCriteria.empty(), request(9, 3));

        assertEquals(List.of("SKU-P4", "SKU-P5", "SKU-P6"), skus(second));
        assertEquals(7, second.totalElements());
        assertEquals(3, second.totalPages());
        assertEquals(List.of("SKU-P7"), skus(third));
        assertTrue(beyond.items().isEmpty());
        assertEquals(7, beyond.totalElements());
    }

    @Test
    void sortsByRequestedFieldAndDirection() {
        repository.insert(data("SKU-S1", "Charlie", "Cat", "300.00", 2));
        repository.insert(data("SKU-S2", "alpha", "Cat", "100.00", 3));
        repository.insert(data("SKU-S3", "Bravo", "Cat", "200.00", 1));

        assertEquals(List.of("SKU-S2", "SKU-S3", "SKU-S1"),
                skus(repository.search(ProductSearchCriteria.empty(), new PageRequest(0, 10, SortField.NAME, SortDirection.ASC))));
        assertEquals(List.of("SKU-S1", "SKU-S3", "SKU-S2"),
                skus(repository.search(ProductSearchCriteria.empty(), new PageRequest(0, 10, SortField.PRICE, SortDirection.DESC))));
        assertEquals(List.of("SKU-S3", "SKU-S1", "SKU-S2"),
                skus(repository.search(ProductSearchCriteria.empty(), new PageRequest(0, 10, SortField.STOCK, SortDirection.ASC))));
    }

    protected static ProductData data(String sku, String name, String category, String price, int stock) {
        return new ProductData(sku, name, null, category, new BigDecimal(price), stock);
    }

    private static ProductSearchCriteria criteria(String keyword, String category, BigDecimal min, BigDecimal max,
                                                  boolean inStockOnly) {
        return new ProductSearchCriteria(keyword, category, min, max, inStockOnly);
    }

    private static PageRequest request(int page, int size) {
        return new PageRequest(page, size, SortField.ID, SortDirection.ASC);
    }

    private static List<String> skus(Page<Product> page) {
        return page.items().stream().map(Product::sku).toList();
    }
}
