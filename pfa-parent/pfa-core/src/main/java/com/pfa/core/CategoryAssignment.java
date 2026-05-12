package com.pfa.core;

import java.util.Objects;

/**
 * The result of categorizing a transaction: the assigned category and the reason for the assignment.
 */
public record CategoryAssignment(Category category, String reason) {

    public CategoryAssignment {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
