package com.infy.catalog.repository;

/** Unexpected storage failure. Reported to clients only as INTERNAL_ERROR with a traceId. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
