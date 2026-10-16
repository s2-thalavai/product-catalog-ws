package com.infy.catalog.service;

import com.infy.catalog.domain.Page;
import com.infy.catalog.domain.PageRequest;
import com.infy.catalog.domain.Product;
import com.infy.catalog.domain.ProductData;
import com.infy.catalog.domain.ProductSearchCriteria;
import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.SortField;
import com.infy.catalog.domain.exception.InvalidRequestException;
import com.infy.catalog.domain.exception.ProductNotFoundException;
import com.infy.catalog.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Business rules for the catalog. Knows nothing about SOAP, so it can be reused
 * behind a REST controller later in the course.
 */
public class ProductService {

    private final ProductRepository repository;
    private final PagingPolicy paging;

    public ProductService(ProductRepository repository, PagingPolicy paging) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.paging = Objects.requireNonNull(paging, "paging");
    }

    public Product create(ProductData data) {
        return repository.insert(ProductValidator.validated(data));
    }

    public Product get(long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /** Full replace of the writable fields (PUT semantics). */
    public Product update(long id, ProductData data) {
        ProductData valid = ProductValidator.validated(data);
        return repository.update(id, valid).orElseThrow(() -> new ProductNotFoundException(id));
    }

    public void delete(long id) {
        if (!repository.deleteById(id)) {
            throw new ProductNotFoundException(id);
        }
    }

    /**
     * Filtered, sorted, paginated search.
     *
     * @param page 0-based; null means 0
     * @param size null or 0 means the default size; values above the maximum are capped
     * @param sort null means ID
     * @param direction null means ASC
     */
    public Page<Product> search(ProductSearchCriteria criteria, Integer page, Integer size,
                                SortField sort, SortDirection direction) {
        ProductSearchCriteria filters = criteria == null ? ProductSearchCriteria.empty() : criteria.normalized();

        List<String> errors = new ArrayList<>();
        int pageIndex = page == null ? 0 : page;
        if (pageIndex < 0) {
            errors.add("page must be >= 0");
        }
        if (size != null && size < 0) {
            errors.add("size must be >= 0");
        }
        if (filters.keyword() != null && filters.keyword().length() > ProductValidator.KEYWORD_MAX) {
            errors.add("keyword must be at most " + ProductValidator.KEYWORD_MAX + " characters");
        }
        if (filters.category() != null && filters.category().length() > ProductValidator.CATEGORY_MAX) {
            errors.add("category must be at most " + ProductValidator.CATEGORY_MAX + " characters");
        }
        if (filters.minPrice() != null && filters.minPrice().signum() < 0) {
            errors.add("minPrice must be >= 0");
        }
        if (filters.maxPrice() != null && filters.maxPrice().signum() < 0) {
            errors.add("maxPrice must be >= 0");
        }
        if (filters.minPrice() != null && filters.maxPrice() != null
                && filters.minPrice().compareTo(filters.maxPrice()) > 0) {
            errors.add("minPrice must be <= maxPrice");
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        PageRequest request = new PageRequest(
                pageIndex,
                paging.resolveSize(size),
                sort == null ? SortField.ID : sort,
                direction == null ? SortDirection.ASC : direction);
        return repository.search(filters, request);
    }
}
