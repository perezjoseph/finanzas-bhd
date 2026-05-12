package com.pfa.core;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default implementation of {@link FilterEngine}.
 * Builds a composite predicate from {@link FilterCriteria} fields (logical AND)
 * and applies it via linear scan with short-circuiting.
 */
public class DefaultFilterEngine implements FilterEngine {

    @Override
    public TransactionSet apply(Collection<Transaction> all, FilterCriteria criteria) {
        if (all == null) {
            throw new NullPointerException("transactions collection must not be null");
        }

        // Null or empty criteria matches everything
        if (criteria == null || isEmpty(criteria)) {
            return new TransactionSet(List.copyOf(all), criteria);
        }

        Predicate<Transaction> composite = buildPredicate(criteria);

        List<Transaction> filtered = all.stream()
                .filter(composite)
                .toList();

        return new TransactionSet(filtered, criteria);
    }

    private boolean isEmpty(FilterCriteria criteria) {
        return criteria.startDate().isEmpty()
                && criteria.endDate().isEmpty()
                && criteria.accountIds().isEmpty()
                && criteria.currencies().isEmpty()
                && criteria.categoryNames().isEmpty()
                && criteria.merchantSubstring().isEmpty()
                && criteria.minAmount().isEmpty()
                && criteria.maxAmount().isEmpty()
                && criteria.keyword().isEmpty();
    }

    private Predicate<Transaction> buildPredicate(FilterCriteria criteria) {
        Predicate<Transaction> predicate = tx -> true;

        // Date range: start date
        var startOpt = criteria.startDate();
        if (startOpt.isPresent()) {
            var start = startOpt.get();
            predicate = predicate.and(tx -> !tx.date().isBefore(start));
        }

        // Date range: end date
        var endOpt = criteria.endDate();
        if (endOpt.isPresent()) {
            var end = endOpt.get();
            predicate = predicate.and(tx -> !tx.date().isAfter(end));
        }

        // Account filter
        if (!criteria.accountIds().isEmpty()) {
            var accounts = criteria.accountIds();
            predicate = predicate.and(tx -> accounts.contains(tx.accountId()));
        }

        // Currency filter
        if (!criteria.currencies().isEmpty()) {
            var currencies = criteria.currencies();
            predicate = predicate.and(tx -> currencies.contains(tx.amount().currency()));
        }

        // Category filter
        if (!criteria.categoryNames().isEmpty()) {
            var categories = criteria.categoryNames();
            predicate = predicate.and(tx -> categories.contains(tx.category().orElse("")));
        }

        // Merchant substring (case-insensitive)
        var merchantOpt = criteria.merchantSubstring();
        if (merchantOpt.isPresent()) {
            var substring = merchantOpt.get().toLowerCase();
            predicate = predicate.and(tx -> tx.description().toLowerCase().contains(substring));
        }

        // Amount range: minimum
        var minOpt = criteria.minAmount();
        if (minOpt.isPresent()) {
            var min = minOpt.get();
            predicate = predicate.and(tx -> tx.amount().amount().compareTo(min) >= 0);
        }

        // Amount range: maximum
        var maxOpt = criteria.maxAmount();
        if (maxOpt.isPresent()) {
            var max = maxOpt.get();
            predicate = predicate.and(tx -> tx.amount().amount().compareTo(max) <= 0);
        }

        // Keyword: case-insensitive partial match against description AND tags
        var kwOpt = criteria.keyword();
        if (kwOpt.isPresent()) {
            var kw = kwOpt.get().toLowerCase();
            predicate = predicate.and(tx -> {
                if (tx.description().toLowerCase().contains(kw)) {
                    return true;
                }
                return tx.tags().stream()
                        .anyMatch(tag -> tag.toLowerCase().contains(kw));
            });
        }

        return predicate;
    }
}
