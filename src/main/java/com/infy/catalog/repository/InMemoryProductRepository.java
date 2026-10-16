package com.infy.catalog.repository;

import com.infy.catalog.domain.Page;
import com.infy.catalog.domain.PageRequest;
import com.infy.catalog.domain.Product;
import com.infy.catalog.domain.ProductData;
import com.infy.catalog.domain.ProductSearchCriteria;
import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.exception.DuplicateSkuException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory store for local runs and fast tests. Writes are synchronized
 * so the SKU uniqueness check and the write are atomic; reads are lock-free.
 * Not for multi-replica deployments: each pod would have its own data.
 */
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Clock clock;

    public InMemoryProductRepository() {
        this(Clock.systemUTC());
    }

    public InMemoryProductRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized Product insert(ProductData data) {
        ensureSkuAvailable(data.sku(), null);
        Instant now = now();
        Product product = new Product(sequence.incrementAndGet(), data.sku(), data.name(), data.description(),
                data.category(), data.price(), data.stock(), now, now);
        products.put(product.id(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(long id) {
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public synchronized Optional<Product> update(long id, ProductData data) {
        Product existing = products.get(id);
        if (existing == null) {
            return Optional.empty();
        }
        ensureSkuAvailable(data.sku(), id);
        Product updated = new Product(id, data.sku(), data.name(), data.description(), data.category(),
                data.price(), data.stock(), existing.createdAt(), now());
        products.put(id, updated);
        return Optional.of(updated);
    }

    @Override
    public synchronized boolean deleteById(long id) {
        return products.remove(id) != null;
    }

    @Override
    public Page<Product> search(ProductSearchCriteria criteria, PageRequest pageRequest) {
        List<Product> matches = products.values().stream()
                .filter(product -> matches(product, criteria))
                .sorted(comparator(pageRequest))
                .toList();
        List<Product> items = matches.stream()
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
        return new Page<>(items, pageRequest.page(), pageRequest.size(), matches.size());
    }

    private void ensureSkuAvailable(String sku, Long ownerId) {
        boolean taken = products.values().stream()
                .anyMatch(p -> p.sku().equals(sku) && !p.id().equals(ownerId));
        if (taken) {
            throw new DuplicateSkuException(sku);
        }
    }

    private Instant now() {
        // PostgreSQL stores microseconds; truncate so both stores behave the same.
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean matches(Product product, ProductSearchCriteria criteria) {
        if (criteria.keyword() != null) {
            String keyword = criteria.keyword().toLowerCase(Locale.ROOT);
            boolean hit = contains(product.name(), keyword)
                    || contains(product.sku(), keyword)
                    || contains(product.description(), keyword);
            if (!hit) {
                return false;
            }
        }
        if (criteria.category() != null && !criteria.category().equalsIgnoreCase(product.category())) {
            return false;
        }
        if (criteria.minPrice() != null && product.price().compareTo(criteria.minPrice()) < 0) {
            return false;
        }
        if (criteria.maxPrice() != null && product.price().compareTo(criteria.maxPrice()) > 0) {
            return false;
        }
        return !criteria.inStockOnly() || product.stock() > 0;
    }

    private static boolean contains(String value, String lowerCaseKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseKeyword);
    }

    private static Comparator<Product> comparator(PageRequest request) {
        Comparator<Product> primary = switch (request.sort()) {
            case ID -> Comparator.comparing(Product::id);
            case NAME -> Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER);
            case PRICE -> Comparator.comparing(Product::price);
            case STOCK -> Comparator.comparingInt(Product::stock);
            case CREATED_AT -> Comparator.comparing(Product::createdAt);
        };
        if (request.direction() == SortDirection.DESC) {
            primary = primary.reversed();
        }
        return primary.thenComparing(Product::id);
    }
}
