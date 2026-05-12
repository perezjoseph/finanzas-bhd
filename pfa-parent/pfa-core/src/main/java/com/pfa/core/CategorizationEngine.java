package com.pfa.core;

import java.util.List;

/**
 * Assigns categories to transactions using a three-tier lookup:
 * learned overrides, built-in rules, and fallback to Miscellaneous.
 */
public interface CategorizationEngine {

    /**
     * Assigns a category to the given transaction.
     */
    CategoryAssignment assign(Transaction tx);

    /**
     * Records a user override, learning the association between the transaction's
     * normalized merchant key and the given category name.
     */
    void recordOverride(Transaction tx, String categoryName);

    /**
     * Returns all available categories (predefined + custom).
     */
    List<Category> allCategories();

    /**
     * Creates a new custom category with the given name.
     * Name must be 1–50 characters and unique.
     */
    Category createCustomCategory(String name);
}
