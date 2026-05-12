package com.pfa.core;

import java.util.Objects;

/**
 * A learned categorization rule mapping a normalized merchant key to a category.
 * Created when the user overrides a transaction's category.
 */
public record CategoryRule(String merchantKey, String categoryName) {

    public CategoryRule {
        Objects.requireNonNull(merchantKey, "merchantKey must not be null");
        Objects.requireNonNull(categoryName, "categoryName must not be null");
    }
}
