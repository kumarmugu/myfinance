package com.myfinance.config;

import java.util.List;

public class ReferenceConstraintException extends RuntimeException {
    private final List<String> references;

    public ReferenceConstraintException(String entityName, List<String> references) {
        super("Cannot delete " + entityName + ". It is referenced by: " + String.join(", ", references));
        this.references = references;
    }

    public List<String> getReferences() {
        return references;
    }
}
