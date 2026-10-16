package com.infy.catalog.domain.exception;

public class DuplicateSkuException extends CatalogException {

    private final String sku;

    public DuplicateSkuException(String sku) {
        super("A product with SKU '" + sku + "' already exists");
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
