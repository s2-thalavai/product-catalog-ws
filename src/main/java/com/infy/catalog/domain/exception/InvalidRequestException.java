package com.infy.catalog.domain.exception;

import java.util.List;

/** Input failed validation. Carries every violation so the client can fix them in one go. */
public class InvalidRequestException extends CatalogException {

    private final List<String> violations;

    public InvalidRequestException(List<String> violations) {
        super("Request validation failed");
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
