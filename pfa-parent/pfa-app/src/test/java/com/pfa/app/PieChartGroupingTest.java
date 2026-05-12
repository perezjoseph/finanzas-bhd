package com.pfa.app;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.pfa.core.CategoryBreakdown;
import com.pfa.core.Currency;
import com.pfa.core.Money;

/**
 * Tests for PieChartGrouping — verifies that categories below 2% of total
 * are grouped into an "Other" segment.
 * Validates: Requirements 7.1
 */
class PieChartGroupingTest {

    @Test
    void categoriesAboveThresholdGetOwnSegment() {
        // All categories are well above 2%
        Map<String, Map<Currency, Money>> byCategory = new LinkedHashMap<>();
        byCategory.put("Groceries", Map.of(Currency.USD, new Money(new BigDecimal("50.00"), Currency.USD)));
        byCategory.put("Dining", Map.of(Currency.USD, new Money(new BigDecimal("30.00"), Currency.USD)));
        byCategory.put("Transport", Map.of(Currency.USD, new Money(new BigDecimal("20.00"), Currency.USD)));

        CategoryBreakdown breakdown = new CategoryBreakdown(byCategory);
        PieChartGrouping.GroupedData result = PieChartGrouping.group(breakdown);

        assertEquals(3, result.segments().size());
        assertTrue(result.segments().containsKey("Groceries"));
        assertTrue(result.segments().containsKey("Dining"));
        assertTrue(result.segments().containsKey("Transport"));
        assertFalse(result.segments().containsKey("Otros"));
    }

    @Test
    void categoriesBelowTwoPercentGroupedIntoOther() {
        // Total = 100. "Tiny" = 1.50 which is 1.5% < 2%
        Map<String, Map<Currency, Money>> byCategory = new LinkedHashMap<>();
        byCategory.put("Groceries", Map.of(Currency.USD, new Money(new BigDecimal("60.00"), Currency.USD)));
        byCategory.put("Dining", Map.of(Currency.USD, new Money(new BigDecimal("38.50"), Currency.USD)));
        byCategory.put("Tiny", Map.of(Currency.USD, new Money(new BigDecimal("1.50"), Currency.USD)));

        CategoryBreakdown breakdown = new CategoryBreakdown(byCategory);
        PieChartGrouping.GroupedData result = PieChartGrouping.group(breakdown);

        assertEquals(3, result.segments().size());
        assertTrue(result.segments().containsKey("Groceries"));
        assertTrue(result.segments().containsKey("Dining"));
        assertTrue(result.segments().containsKey("Otros"));
        assertFalse(result.segments().containsKey("Tiny"));
        assertEquals(new BigDecimal("1.50"), result.segments().get("Otros"));
    }

    @Test
    void multipleSmallCategoriesMergedIntoSingleOther() {
        // Total = 100. Small1 = 0.50 (0.5%), Small2 = 0.80 (0.8%)
        Map<String, Map<Currency, Money>> byCategory = new LinkedHashMap<>();
        byCategory.put("Groceries", Map.of(Currency.USD, new Money(new BigDecimal("98.70"), Currency.USD)));
        byCategory.put("Small1", Map.of(Currency.USD, new Money(new BigDecimal("0.50"), Currency.USD)));
        byCategory.put("Small2", Map.of(Currency.USD, new Money(new BigDecimal("0.80"), Currency.USD)));

        CategoryBreakdown breakdown = new CategoryBreakdown(byCategory);
        PieChartGrouping.GroupedData result = PieChartGrouping.group(breakdown);

        assertEquals(2, result.segments().size());
        assertTrue(result.segments().containsKey("Groceries"));
        assertTrue(result.segments().containsKey("Otros"));
        assertEquals(new BigDecimal("1.30"), result.segments().get("Otros"));
    }

    @Test
    void categoryAtExactlyTwoPercentIsNotGrouped() {
        // Total = 100. "Borderline" = 2.00 which is exactly 2% — should NOT be grouped
        Map<String, Map<Currency, Money>> byCategory = new LinkedHashMap<>();
        byCategory.put("Groceries", Map.of(Currency.USD, new Money(new BigDecimal("98.00"), Currency.USD)));
        byCategory.put("Borderline", Map.of(Currency.USD, new Money(new BigDecimal("2.00"), Currency.USD)));

        CategoryBreakdown breakdown = new CategoryBreakdown(byCategory);
        PieChartGrouping.GroupedData result = PieChartGrouping.group(breakdown);

        assertEquals(2, result.segments().size());
        assertTrue(result.segments().containsKey("Groceries"));
        assertTrue(result.segments().containsKey("Borderline"));
        assertFalse(result.segments().containsKey("Otros"));
    }

    @Test
    void emptyBreakdownReturnsNoSegments() {
        CategoryBreakdown breakdown = new CategoryBreakdown(Map.of());
        PieChartGrouping.GroupedData result = PieChartGrouping.group(breakdown);

        assertTrue(result.segments().isEmpty());
    }

    @Test
    void singleCategoryAlwaysAboveThreshold() {
        Map<String, Map<Currency, Money>> byCategory = new LinkedHashMap<>();
        byCategory.put("Groceries", Map.of(Currency.USD, new Money(new BigDecimal("100.00"), Currency.USD)));

        CategoryBreakdown breakdown = new CategoryBreakdown(byCategory);
        PieChartGrouping.GroupedData result = PieChartGrouping.group(breakdown);

        assertEquals(1, result.segments().size());
        assertTrue(result.segments().containsKey("Groceries"));
    }

    @Test
    void multiCurrencySegmentsAreKeyedByCurrency() {
        // Two currencies present — segments should include currency symbol
        Map<String, Map<Currency, Money>> byCategory = new LinkedHashMap<>();
        byCategory.put("Groceries", Map.of(
                Currency.USD, new Money(new BigDecimal("50.00"), Currency.USD),
                Currency.DOP, new Money(new BigDecimal("30.00"), Currency.DOP)));
        byCategory.put("Dining", Map.of(
                Currency.USD, new Money(new BigDecimal("20.00"), Currency.USD)));

        CategoryBreakdown breakdown = new CategoryBreakdown(byCategory);
        PieChartGrouping.GroupedData result = PieChartGrouping.group(breakdown);

        // All segments should be above 2% of total (100)
        // Groceries (US$) = 50, Groceries (RD$) = 30, Dining (US$) = 20
        assertTrue(result.segments().containsKey("Groceries (" + Currency.USD.symbol + ")"));
        assertTrue(result.segments().containsKey("Groceries (" + Currency.DOP.symbol + ")"));
        assertTrue(result.segments().containsKey("Dining (" + Currency.USD.symbol + ")"));
        assertFalse(result.segments().containsKey("Otros"));
    }
}
