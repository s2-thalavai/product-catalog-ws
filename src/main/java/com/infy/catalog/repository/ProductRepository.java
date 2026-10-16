package com.infy.catalog.repository;

import com.infy.catalog.domain.Page;
import com.infy.catalog.domain.PageRequest;
import com.infy.catalog.domain.Product;
import com.infy.catalog.domain.ProductData;
import com.infy.catalog.domain.ProductSearchCriteria;

import java.util.Optional;

/**
 * Storage port. Implementations receive already-validated data and must throw
 * {@link com.infy.catalog.domain.exception.DuplicateSkuException} when a SKU is taken.
 * Both implementations are verified by the same contract test (ProductRepositoryContract).
 */
public interface ProductRepository {

    Product insert(ProductData data);

    Optional<Product> findById(long id);

    /** Replaces the writable fields; empty if the id does not exist. */
    Optional<Product> update(long id, ProductData data);

    boolean deleteById(long id);

    /** Sorted by the requested field, then by id ascending as a stable tie-breaker. */
    Page<Product> search(ProductSearchCriteria criteria, PageRequest pageRequest);
}
