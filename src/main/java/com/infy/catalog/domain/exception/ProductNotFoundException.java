package com.infy.catalog.domain.exception;

public class ProductNotFoundException extends CatalogException {

    private final long productId;

    public ProductNotFoundException(long productId) {
        super("Product " + productId + " was not found");
        this.productId = productId;
    }

    public long productId() {
        return productId;
    }
}
