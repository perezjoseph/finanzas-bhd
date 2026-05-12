package com.pfa.core;

import java.util.Objects;

/**
 * A transaction category. Name must be 1–50 characters, non-null, and non-empty.
 */
public record Category(String name, boolean isCustom) {

    public Category {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("Category name must be at most 50 characters, got " + name.length());
        }
    }
}
