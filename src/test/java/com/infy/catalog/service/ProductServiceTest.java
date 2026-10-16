package com.infy.catalog.service;

import com.infy.catalog.domain.Page;
import com.infy.catalog.domain.Product;
import com.infy.catalog.domain.ProductData;
import com.infy.catalog.domain.ProductSearchCriteria;
import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.SortField;
import com.infy.catalog.domain.exception.DuplicateSkuException;
import com.infy.catalog.domain.exception.InvalidRequestException;
import com.infy.catalog.domain.exception.ProductNotFoundException;
import com.infy.catalog.repository.InMemoryProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductServiceTest {

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(new InMemoryProductRepository(), new PagingPolicy(10, 50));
    }

    @Test
    void createNormalizesInputAndAssignsId() {
        Product created = service.create(
                new ProductData("  elec-001 ", "  Wireless Mouse ", "   ", " Electronics ", new BigDecimal("799.5"), 10));

        assertNotNull(created.id());
        assertEquals("ELEC-001", created.sku());
        assertEquals("Wireless Mouse", created.name());
        assertNull(created.description());
        assertEquals("Electronics", created.category());
        assertEquals(new BigDecimal("799.50"), created.price());
    }

    @Test
    void createReportsEveryViolationAtOnce() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.create(new ProductData("x", "", null, "c".repeat(61), new BigDecimal("1.234"), -1)));

        List<String> violations = e.violations();
        assertEquals(5, violations.size(), violations::toString);
        assertTrue(violations.contains("name is required"));
        assertTrue(violations.contains("price must have at most 2 decimal places"));
    }

    @Test
    void createRejectsMissingRequiredFieldsAndNullProduct() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.create(new ProductData(null, null, null, null, null, null)));
        assertEquals(List.of("sku is required", "name is required", "price is required", "stock is required"),
                e.violations());

        assertThrows(InvalidRequestException.class, () -> service.create(null));
    }

    @Test
    void createRejectsDuplicateSkuCaseInsensitively() {
        service.create(product("SKU-100", "First", "Home", "10.00", 1));

        assertThrows(DuplicateSkuException.class, () -> service.create(product("sku-100", "Second", "Home", "10.00", 1)));
    }

    @Test
    void getUpdateDeleteUnknownIdThrowNotFound() {
        assertThrows(ProductNotFoundException.class, () -> service.get(404));
        assertThrows(ProductNotFoundException.class, () -> service.update(404, product("SKU-404", "X", "Home", "1.00", 1)));
        assertThrows(ProductNotFoundException.class, () -> service.delete(404));
    }

    @Test
    void updateReplacesAllWritableFields() {
        Product created = service.create(product("SKU-200", "Lamp", "Home", "1299.00", 5));

        Product updated = service.update(created.id(), product("SKU-200", "Desk Lamp", "Office", "999.00", 0));

        assertEquals("Desk Lamp", updated.name());
        assertEquals("Office", updated.category());
        assertEquals(0, updated.stock());
        assertEquals(created.createdAt(), updated.createdAt());
    }

    @Test
    void deleteRemovesTheProduct() {
        Product created = service.create(product("SKU-300", "Mug", "Home", "249.00", 3));

        service.delete(created.id());

        assertThrows(ProductNotFoundException.class, () -> service.get(created.id()));
    }

    @Test
    void searchCombinesKeywordCategoryPriceAndStockFilters() {
        service.create(product("SKU-A", "Wireless Mouse", "Electronics", "799.00", 10));
        service.create(product("SKU-B", "Wired Mouse", "Electronics", "299.00", 10));
        service.create(product("SKU-C", "Mouse Pad", "Office", "199.00", 10));
        service.create(product("SKU-D", "Gaming Mouse", "Electronics", "2999.00", 0));

        Page<Product> page = service.search(
                new ProductSearchCriteria(" mouse ", "ELECTRONICS", new BigDecimal("200"), new BigDecimal("3000"), true),
                null, null, null, null);

        assertEquals(2, page.totalElements());
        assertEquals(List.of("SKU-A", "SKU-B"), page.items().stream().map(Product::sku).toList());
    }

    @Test
    void searchPaginatesAndReportsNavigation() {
        for (int i = 1; i <= 25; i++) {
            service.create(product("SKU-P" + i, "Item " + i, "Paging", "10.00", 1));
        }

        Page<Product> first = service.search(ProductSearchCriteria.empty(), 0, 10, SortField.ID, SortDirection.ASC);
        Page<Product> last = service.search(ProductSearchCriteria.empty(), 2, 10, SortField.ID, SortDirection.ASC);
        Page<Product> beyond = service.search(ProductSearchCriteria.empty(), 5, 10, SortField.ID, SortDirection.ASC);

        assertEquals(10, first.items().size());
        assertEquals(3, first.totalPages());
        assertTrue(first.hasNext());
        assertFalse(first.hasPrevious());
        assertEquals(5, last.items().size());
        assertFalse(last.hasNext());
        assertTrue(beyond.items().isEmpty());
        assertEquals(25, beyond.totalElements());
    }

    @Test
    void searchAppliesDefaultSizeAndCapsLargeSizes() {
        assertEquals(10, service.search(null, null, null, null, null).size());
        assertEquals(10, service.search(null, 0, 0, null, null).size());
        assertEquals(50, service.search(null, 0, 10_000, null, null).size());
    }

    @Test
    void searchSortsByPriceDescendingWithIdTieBreak() {
        service.create(product("SKU-S1", "Cheap", "Sort", "100.00", 1));
        service.create(product("SKU-S2", "Expensive", "Sort", "900.00", 1));
        service.create(product("SKU-S3", "Also cheap", "Sort", "100.00", 1));

        Page<Product> page = service.search(
                new ProductSearchCriteria(null, "Sort", null, null, false), 0, 10, SortField.PRICE, SortDirection.DESC);

        assertEquals(List.of("SKU-S2", "SKU-S1", "SKU-S3"), page.items().stream().map(Product::sku).toList());
    }

    @Test
    void searchRejectsInvalidPagingAndPriceRange() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> service.search(new ProductSearchCriteria("k".repeat(101), null, new BigDecimal("500"),
                        new BigDecimal("100"), false), -1, -5, null, null));

        assertEquals(List.of("page must be >= 0", "size must be >= 0", "keyword must be at most 100 characters",
                "minPrice must be <= maxPrice"), e.violations());
    }

    private static ProductData product(String sku, String name, String category, String price, int stock) {
        return new ProductData(sku, name, null, category, new BigDecimal(price), stock);
    }
}
