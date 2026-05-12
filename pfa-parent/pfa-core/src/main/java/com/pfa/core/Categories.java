package com.pfa.core;

import java.util.List;

/**
 * Predefined spending categories (Requirement 5.1).
 * All 18 built-in categories are exposed as constants and as an unmodifiable list.
 */
public final class Categories {

    private Categories() {
        // utility class
    }

    public static final Category FOOD_AND_DINING = new Category("Food & Dining", false);
    public static final Category GROCERIES = new Category("Groceries", false);
    public static final Category UTILITIES = new Category("Utilities", false);
    public static final Category RENT_HOUSING = new Category("Rent/Housing", false);
    public static final Category TRANSPORTATION = new Category("Transportation", false);
    public static final Category FUEL = new Category("Fuel", false);
    public static final Category ENTERTAINMENT = new Category("Entertainment", false);
    public static final Category TRAVEL = new Category("Travel", false);
    public static final Category HEALTHCARE = new Category("Healthcare", false);
    public static final Category SHOPPING = new Category("Shopping", false);
    public static final Category INCOME = new Category("Income", false);
    public static final Category INVESTMENTS = new Category("Investments", false);
    public static final Category TRANSFERS = new Category("Transfers", false);
    public static final Category FEES = new Category("Fees", false);
    public static final Category SUBSCRIPTIONS = new Category("Subscriptions", false);
    public static final Category CASH_WITHDRAWALS = new Category("Cash Withdrawals", false);
    public static final Category TAXES = new Category("Taxes", false);
    public static final Category MISCELLANEOUS = new Category("Miscellaneous", false);

    /**
     * All 18 predefined categories in declaration order.
     */
    public static final List<Category> ALL = List.of(
            FOOD_AND_DINING,
            GROCERIES,
            UTILITIES,
            RENT_HOUSING,
            TRANSPORTATION,
            FUEL,
            ENTERTAINMENT,
            TRAVEL,
            HEALTHCARE,
            SHOPPING,
            INCOME,
            INVESTMENTS,
            TRANSFERS,
            FEES,
            SUBSCRIPTIONS,
            CASH_WITHDRAWALS,
            TAXES,
            MISCELLANEOUS
    );
}
