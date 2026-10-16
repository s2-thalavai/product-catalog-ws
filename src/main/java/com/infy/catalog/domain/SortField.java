package com.infy.catalog.domain;

/** Whitelisted sort keys. Repositories map these to columns; client input never reaches SQL. */
public enum SortField {
    ID,
    NAME,
    PRICE,
    STOCK,
    CREATED_AT
}
