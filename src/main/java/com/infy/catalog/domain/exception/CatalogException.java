package com.infy.catalog.domain.exception;

/** Base type for expected business failures. The SOAP layer maps each subtype to a fault code. */
public abstract class CatalogException extends RuntimeException {

    protected CatalogException(String message) {
        super(message);
    }
}
