package com.infy.catalog.ws;

import com.infy.catalog.domain.Page;
import com.infy.catalog.domain.Product;
import com.infy.catalog.domain.ProductData;
import com.infy.catalog.domain.ProductSearchCriteria;
import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.SortField;
import com.infy.catalog.domain.exception.InvalidRequestException;
import com.infy.catalog.ws.dto.ProductDto;
import com.infy.catalog.ws.dto.ProductInput;
import com.infy.catalog.ws.dto.ProductPage;
import com.infy.catalog.ws.dto.SearchRequest;
import com.infy.catalog.ws.dto.SortBy;
import com.infy.catalog.ws.dto.SortOrder;

import java.util.List;

/** Converts between the published XML types and the domain. Keeps the contract and the domain independent. */
public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductData toData(ProductInput input) {
        if (input == null) {
            throw new InvalidRequestException(List.of("product is required"));
        }
        return new ProductData(input.getSku(), input.getName(), input.getDescription(), input.getCategory(),
                input.getPrice(), input.getStock());
    }

    public static ProductDto toDto(Product product) {
        return new ProductDto(product.id(), product.sku(), product.name(), product.description(),
                product.category(), product.price(), product.stock(), product.createdAt(), product.updatedAt());
    }

    public static ProductPage toPage(Page<Product> page) {
        List<ProductDto> items = page.items().stream().map(ProductMapper::toDto).toList();
        return new ProductPage(items, page.page(), page.size(), page.totalElements(),
                page.totalPages(), page.hasNext(), page.hasPrevious());
    }

    public static ProductSearchCriteria toCriteria(SearchRequest request) {
        if (request == null) {
            return ProductSearchCriteria.empty();
        }
        return new ProductSearchCriteria(request.getKeyword(), request.getCategory(), request.getMinPrice(),
                request.getMaxPrice(), Boolean.TRUE.equals(request.getInStockOnly()));
    }

    public static SortField toSortField(SortBy sortBy) {
        if (sortBy == null) {
            return null;
        }
        return switch (sortBy) {
            case ID -> SortField.ID;
            case NAME -> SortField.NAME;
            case PRICE -> SortField.PRICE;
            case STOCK -> SortField.STOCK;
            case CREATED_AT -> SortField.CREATED_AT;
        };
    }

    public static SortDirection toDirection(SortOrder order) {
        if (order == null) {
            return null;
        }
        return order == SortOrder.DESC ? SortDirection.DESC : SortDirection.ASC;
    }
}
