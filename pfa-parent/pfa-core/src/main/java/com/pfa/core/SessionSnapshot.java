package com.pfa.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contains all data needed to fully restore a session: transactions, accounts,
 * categories, learned categorization rules, exchange rates, budgets, and settings.
 */
public record SessionSnapshot(
        String schemaVersion,
        List<Account> accounts,
        List<Transaction> transactions,
        List<Category> categories,
        List<CategoryRule> learnedRules,
        List<ExchangeRate> rates,
        List<Budget> budgets,
        Map<String, String> settings
) {

    public SessionSnapshot {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(accounts, "accounts must not be null");
        Objects.requireNonNull(transactions, "transactions must not be null");
        Objects.requireNonNull(categories, "categories must not be null");
        Objects.requireNonNull(learnedRules, "learnedRules must not be null");
        Objects.requireNonNull(rates, "rates must not be null");
        Objects.requireNonNull(budgets, "budgets must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        // Defensive copies for immutability
        accounts = List.copyOf(accounts);
        transactions = List.copyOf(transactions);
        categories = List.copyOf(categories);
        learnedRules = List.copyOf(learnedRules);
        rates = List.copyOf(rates);
        budgets = List.copyOf(budgets);
        settings = Map.copyOf(settings);
    }
}
