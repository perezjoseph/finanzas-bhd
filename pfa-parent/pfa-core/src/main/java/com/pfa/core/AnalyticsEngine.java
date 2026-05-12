package com.pfa.core;

import java.util.List;

/**
 * Stateless computations over a TransactionSet. Returns immutable result records.
 * All multi-currency aggregations convert via CurrencyConverter only for computed totals;
 * per-currency breakdowns keep original currencies.
 */
public interface AnalyticsEngine {

    /**
     * Computes monthly spending trends grouped by category.
     */
    MonthlyTrends monthlyTrends(TransactionSet txs, Currency base);

    /**
     * Computes spending breakdown by category.
     */
    CategoryBreakdown categoryBreakdown(TransactionSet txs);

    /**
     * Computes spending breakdown by currency.
     */
    CurrencyBreakdown currencyBreakdown(TransactionSet txs);

    /**
     * Computes spending breakdown by account.
     */
    AccountBreakdown accountBreakdown(TransactionSet txs);

    /**
     * Computes the average monthly burn rate in the given base currency.
     */
    Money averageBurnRate(TransactionSet txs, Currency base);

    /**
     * Returns the top N largest expenses within the given date range.
     */
    List<Transaction> topExpenses(TransactionSet txs, DateRange range, int limit);

    /**
     * Detects recurring payments based on transaction patterns.
     */
    List<RecurringPayment> detectRecurring(TransactionSet txs);

    /**
     * Identifies unusual spending spikes compared to historical averages.
     */
    List<SpendingAlert> unusualSpending(TransactionSet txs);

    /**
     * Forecasts end-of-month spending based on current month data.
     * Requires at least 7 days of data in the current month.
     */
    MonthlyForecast forecastCurrentMonth(TransactionSet txs, Currency base);

    /**
     * Computes budget status for each budget against actual spending.
     */
    List<BudgetStatus> budgetStatus(TransactionSet txs, List<Budget> budgets, Currency base);

    /**
     * Computes net worth trend across accounts over time.
     */
    NetWorthTrend netWorthTrend(List<AccountBalance> balances);

    /**
     * Computes income versus expenses comparison per calendar month,
     * showing total income (CREDIT) and total expenses (DEBIT) as separate values.
     */
    IncomeVsExpenses incomeVsExpenses(TransactionSet txs, Currency base);

    /**
     * Estimates savings rate per month as (total income - total expenses) / total income,
     * expressed as a percentage. Returns null for months with zero income.
     */
    MonthlySavingsRate monthlySavingsRate(TransactionSet txs, Currency base);
}
