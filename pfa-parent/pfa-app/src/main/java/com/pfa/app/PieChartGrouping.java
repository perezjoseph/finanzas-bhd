package com.pfa.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import com.pfa.core.CategoryBreakdown;
import com.pfa.core.Currency;

/**
 * Groups category breakdown data for pie chart display.
 * Categories representing less than 2% of total spending are merged into "Other".
 * When transactions span multiple currencies, segments are keyed per currency.
 */
public final class PieChartGrouping {

    static final BigDecimal THRESHOLD_PERCENT = new BigDecimal("0.02");

    private PieChartGrouping() {
    }

    /**
     * Result of grouping categories for pie chart display.
     * Each entry maps a display label to its total amount.
     */
    public record GroupedData(Map<String, BigDecimal> segments) {
    }

    /**
     * Groups categories from a breakdown into pie chart segments.
     * Categories with less than 2% of total spending are merged into an "Other" segment.
     * When multiple currencies are present, segments are keyed as "Category (CUR)".
     *
     * @param breakdown the category breakdown (may contain multi-currency values)
     * @return grouped segments suitable for pie chart display
     */
    public static GroupedData group(CategoryBreakdown breakdown) {
        // Determine if transactions span multiple currencies
        boolean multiCurrency = breakdown.byCategory().values().stream()
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .count() > 1;

        // Build per-segment totals
        BigDecimal grandTotal = BigDecimal.ZERO;
        Map<String, BigDecimal> segmentTotals = new LinkedHashMap<>();

        for (var entry : breakdown.byCategory().entrySet()) {
            String category = entry.getKey();
            for (var currEntry : entry.getValue().entrySet()) {
                Currency currency = currEntry.getKey();
                BigDecimal amount = currEntry.getValue().amount();
                String segmentKey = multiCurrency
                        ? category + " (" + currency.symbol + ")"
                        : category;
                segmentTotals.merge(segmentKey, amount, BigDecimal::add);
                grandTotal = grandTotal.add(amount);
            }
        }

        Map<String, BigDecimal> segments = new LinkedHashMap<>();

        if (grandTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new GroupedData(segments);
        }

        BigDecimal threshold = grandTotal.multiply(THRESHOLD_PERCENT);
        BigDecimal otherTotal = BigDecimal.ZERO;

        for (var entry : segmentTotals.entrySet()) {
            if (entry.getValue().compareTo(threshold) >= 0) {
                segments.put(entry.getKey(), entry.getValue());
            } else {
                otherTotal = otherTotal.add(entry.getValue());
            }
        }

        if (otherTotal.compareTo(BigDecimal.ZERO) > 0) {
            segments.put("Otros", otherTotal.setScale(2, RoundingMode.HALF_UP));
        }

        return new GroupedData(segments);
    }
}
