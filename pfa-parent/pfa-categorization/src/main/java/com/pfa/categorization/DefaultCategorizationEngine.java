package com.pfa.categorization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.pfa.core.Categories;
import com.pfa.core.CategorizationEngine;
import com.pfa.core.Category;
import com.pfa.core.CategoryAssignment;
import com.pfa.core.CategoryRule;
import com.pfa.core.Transaction;

/**
 * Three-tier categorization engine:
 * 1. Learned overrides (exact match on normalized merchant key)
 * 2. Built-in rules (ordered predicate list with BHD-specific patterns)
 * 3. Fallback to Miscellaneous
 */
public class DefaultCategorizationEngine implements CategorizationEngine {

    private final Map<String, String> learnedOverrides = new LinkedHashMap<>();
    private final List<BuiltInRule> builtInRules;
    private final List<Category> customCategories = new ArrayList<>();

    public DefaultCategorizationEngine() {
        this.builtInRules = initBuiltInRules();
    }

    public DefaultCategorizationEngine(List<CategoryRule> existingRules) {
        this();
        for (CategoryRule rule : existingRules) {
            learnedOverrides.put(rule.merchantKey(), rule.categoryName());
        }
    }

    @Override
    public CategoryAssignment assign(Transaction tx) {
        String merchantKey = normalizeMerchantKey(tx.description());

        // Tier 1: Learned overrides
        String overrideCategory = learnedOverrides.get(merchantKey);
        if (overrideCategory != null) {
            return new CategoryAssignment(findOrCreate(overrideCategory), "learned override");
        }

        // Tier 2: Built-in rules
        for (BuiltInRule rule : builtInRules) {
            if (rule.matches(tx)) {
                return new CategoryAssignment(rule.category(), "built-in rule: " + rule.name());
            }
        }

        // Tier 3: Fallback
        return new CategoryAssignment(Categories.MISCELLANEOUS, "fallback");
    }

    @Override
    public void recordOverride(Transaction tx, String categoryName) {
        String merchantKey = normalizeMerchantKey(tx.description());
        learnedOverrides.put(merchantKey, categoryName);
    }

    @Override
    public List<Category> allCategories() {
        List<Category> all = new ArrayList<>(Categories.ALL);
        all.addAll(customCategories);
        return List.copyOf(all);
    }

    @Override
    public Category createCustomCategory(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("Category name must be at most 50 characters");
        }

        // Check for duplicates
        for (Category existing : allCategories()) {
            if (existing.name().equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("Category already exists: " + name);
            }
        }

        Category custom = new Category(name, true);
        customCategories.add(custom);
        return custom;
    }

    /**
     * Returns all learned rules for persistence.
     */
    public List<CategoryRule> getLearnedRules() {
        return learnedOverrides.entrySet().stream()
                .map(e -> new CategoryRule(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Normalizes a merchant key: lowercase, trimmed, punctuation stripped.
     */
    static String normalizeMerchantKey(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        return description.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Category findOrCreate(String categoryName) {
        for (Category c : Categories.ALL) {
            if (c.name().equalsIgnoreCase(categoryName)) {
                return c;
            }
        }
        for (Category c : customCategories) {
            if (c.name().equalsIgnoreCase(categoryName)) {
                return c;
            }
        }
        return new Category(categoryName, true);
    }

    private List<BuiltInRule> initBuiltInRules() {
        List<BuiltInRule> rules = new ArrayList<>();

        // BHD-specific patterns
        rules.add(rule("CRTRINTL transfer", contains("CRTRINTL"), Categories.TRANSFERS));
        rules.add(rule("TC payment", contains("PAGO DE TC"), Categories.TRANSFERS));
        rules.add(rule("MBP payment", contains("PAGO DEBITO A CUENTA MBP"), Categories.TRANSFERS));
        rules.add(rule("Law 288-04 tax", contains("Ley 288-04"), Categories.TAXES));
        rules.add(rule("Law 253-12 tax", contains("Ret.ley 253-12"), Categories.TAXES));
        rules.add(rule("Impuesto 0.15%", contains("Impuesto 0.15%"), Categories.TAXES));
        rules.add(rule("Interest income", contains("Pago Intereses CA"), Categories.INCOME));

        // Merchant patterns
        rules.add(rule("Massy Stores", contains("MASSY STORES"), Categories.GROCERIES));
        rules.add(rule("PayPal subscription", contains("PAYPAL"), Categories.SUBSCRIPTIONS));

        // Generic patterns
        rules.add(rule("ATM withdrawal", contains("ATM"), Categories.CASH_WITHDRAWALS));
        rules.add(rule("Fuel station", containsAny("GASOLINA", "SHELL", "TEXACO", "ESSO"), Categories.FUEL));

        return List.copyOf(rules);
    }

    private static BuiltInRule rule(String name, Predicate<Transaction> predicate, Category category) {
        return new BuiltInRule(name, predicate, category);
    }

    private static Predicate<Transaction> contains(String pattern) {
        String upper = pattern.toUpperCase();
        return tx -> tx.description().toUpperCase().contains(upper);
    }

    private static Predicate<Transaction> containsAny(String... patterns) {
        return tx -> {
            String desc = tx.description().toUpperCase();
            for (String p : patterns) {
                if (desc.contains(p.toUpperCase())) {
                    return true;
                }
            }
            return false;
        };
    }

    private record BuiltInRule(String name, Predicate<Transaction> predicate, Category category) {
        boolean matches(Transaction tx) {
            return predicate.test(tx);
        }
    }
}
