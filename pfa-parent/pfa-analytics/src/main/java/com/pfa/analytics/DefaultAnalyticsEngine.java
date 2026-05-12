package com.pfa.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.pfa.core.AccountBalance;
import com.pfa.core.AccountBreakdown;
import com.pfa.core.AnalyticsEngine;
import com.pfa.core.Budget;
import com.pfa.core.BudgetPeriod;
import com.pfa.core.BudgetStatus;
import com.pfa.core.CategoryBreakdown;
import com.pfa.core.Currency;
import com.pfa.core.CurrencyBreakdown;
import com.pfa.core.CurrencyConverter;
import com.pfa.core.DateRange;
import com.pfa.core.Direction;
import com.pfa.core.IncomeVsExpenses;
import com.pfa.core.Money;
import com.pfa.core.MonthlyForecast;
import com.pfa.core.MonthlySavingsRate;
import com.pfa.core.MonthlyTrends;
import com.pfa.core.NetWorthTrend;
import com.pfa.core.RecurringPayment;
import com.pfa.core.SpendingAlert;
import com.pfa.core.Transaction;
import com.pfa.core.TransactionSet;

/**
 * Default implementation of AnalyticsEngine.
 * Provides spending trends, breakdowns, recurring payment detection,
 * unusual spending alerts, forecasts, budget status, and net worth trends.
 */
public class DefaultAnalyticsEngine implements AnalyticsEngine {

    private static final String MISCELLANEOUS = "Miscellaneous";

    private final CurrencyConverter converter;

    public DefaultAnalyticsEngine(CurrencyConverter converter) {
        this.converter = Objects.requireNonNull(converter);
    }

    @Override
    public MonthlyTrends monthlyTrends(TransactionSet txs, Currency base) {
        Map<YearMonth, Map<String, Money>> result = new LinkedHashMap<>();

        for (Transaction tx : txs.transactions()) {
            if (tx.direction() != Direction.DEBIT) {
                continue;
            }
            YearMonth month = YearMonth.from(tx.date());
            String category = tx.category().orElse(MISCELLANEOUS);
            Money converted = converter.convert(tx.amount(), base);

            result.computeIfAbsent(month, k -> new LinkedHashMap<>())
                    .merge(category, converted, Money::plus);
        }

        return new MonthlyTrends(result);
    }

    @Override
    public CategoryBreakdown categoryBreakdown(TransactionSet txs) {
        Map<String, Map<Currency, Money>> byCategory = new LinkedHashMap<>();

        for (Transaction tx : txs.transactions()) {
            if (tx.direction() != Direction.DEBIT) {
                continue;
            }
            String category = tx.category().orElse(MISCELLANEOUS);
            Currency currency = tx.amount().currency();
            byCategory.computeIfAbsent(category, k -> new LinkedHashMap<>())
                    .merge(currency, tx.amount(), Money::plus);
        }

        return new CategoryBreakdown(byCategory);
    }

    @Override
    public CurrencyBreakdown currencyBreakdown(TransactionSet txs) {
        Map<Currency, Money> byCurrency = new EnumMap<>(Currency.class);

        for (Transaction tx : txs.transactions()) {
            if (tx.direction() != Direction.DEBIT) {
                continue;
            }
            byCurrency.merge(tx.amount().currency(), tx.amount(), Money::plus);
        }

        return new CurrencyBreakdown(byCurrency);
    }

    @Override
    public AccountBreakdown accountBreakdown(TransactionSet txs) {
        Map<String, Money> byAccount = new LinkedHashMap<>();

        for (Transaction tx : txs.transactions()) {
            if (tx.direction() != Direction.DEBIT) {
                continue;
            }
            byAccount.merge(tx.accountId(), tx.amount(), Money::plus);
        }

        return new AccountBreakdown(byAccount);
    }

    @Override
    public Money averageBurnRate(TransactionSet txs, Currency base) {
        Map<YearMonth, BigDecimal> monthlyTotals = new LinkedHashMap<>();

        for (Transaction tx : txs.transactions()) {
            if (tx.direction() != Direction.DEBIT) {
                continue;
            }
            YearMonth month = YearMonth.from(tx.date());
            Money converted = converter.convert(tx.amount(), base);
            monthlyTotals.merge(month, converted.amount(), BigDecimal::add);
        }

        if (monthlyTotals.isEmpty()) {
            return new Money(BigDecimal.ZERO, base);
        }

        BigDecimal total = monthlyTotals.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(
                BigDecimal.valueOf(monthlyTotals.size()), 2, RoundingMode.HALF_UP);

        return new Money(average, base);
    }

    @Override
    public List<Transaction> topExpenses(TransactionSet txs, DateRange range, int limit) {
        return txs.transactions().stream()
                .filter(tx -> tx.direction() == Direction.DEBIT)
                .filter(tx -> !tx.date().isBefore(range.start()) && !tx.date().isAfter(range.end()))
                .sorted(Comparator.comparing((Transaction tx) -> tx.amount().amount()).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<RecurringPayment> detectRecurring(TransactionSet txs) {
        // Group by normalized merchant key
        Map<String, List<Transaction>> byMerchant = txs.transactions().stream()
                .filter(tx -> tx.direction() == Direction.DEBIT)
                .collect(Collectors.groupingBy(
                        tx -> tx.description().toLowerCase().trim(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<RecurringPayment> recurring = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : byMerchant.entrySet()) {
            List<Transaction> group = entry.getValue();
            if (group.size() < 3) {
                continue;
            }

            findRecurringPattern(entry.getKey(), group).ifPresent(recurring::add);
        }

        return recurring;
    }

    @Override
    public List<SpendingAlert> unusualSpending(TransactionSet txs) {
        List<SpendingAlert> alerts = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();

        Map<String, List<Money>> historicalByCategory = new LinkedHashMap<>();
        Map<String, Money> currentByCategory = new LinkedHashMap<>();

        for (Transaction tx : txs.transactions()) {
            if (tx.direction() != Direction.DEBIT) {
                continue;
            }
            String category = tx.category().orElse(MISCELLANEOUS);
            YearMonth txMonth = YearMonth.from(tx.date());

            if (txMonth.equals(currentMonth)) {
                currentByCategory.merge(category, tx.amount(), Money::plus);
            } else if (txMonth.isAfter(currentMonth.minusMonths(4))) {
                historicalByCategory.computeIfAbsent(category, k -> new ArrayList<>())
                        .add(tx.amount());
            }
        }

        for (Map.Entry<String, Money> entry : currentByCategory.entrySet()) {
            String category = entry.getKey();
            Money current = entry.getValue();
            List<Money> historical = historicalByCategory.getOrDefault(category, List.of());

            if (historical.size() < 3) {
                continue;
            }

            BigDecimal historicalAvg = historical.stream()
                    .map(Money::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(historical.size()), 2, RoundingMode.HALF_UP);

            BigDecimal threshold = historicalAvg.multiply(new BigDecimal("1.5"));
            if (current.amount().compareTo(threshold) > 0) {
                alerts.add(new SpendingAlert(category, current,
                        new Money(historicalAvg, current.currency())));
            }
        }

        return alerts;
    }

    @Override
    public MonthlyForecast forecastCurrentMonth(TransactionSet txs, Currency base) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate today = LocalDate.now();
        int daysElapsed = today.getDayOfMonth();
        int daysInMonth = currentMonth.lengthOfMonth();

        if (daysElapsed < 7) {
            return new MonthlyForecast(new Money(BigDecimal.ZERO, base), daysElapsed, daysInMonth);
        }

        BigDecimal currentTotal = BigDecimal.ZERO;
        for (Transaction tx : txs.transactions()) {
            if (tx.direction() != Direction.DEBIT) {
                continue;
            }
            if (YearMonth.from(tx.date()).equals(currentMonth)) {
                Money converted = converter.convert(tx.amount(), base);
                currentTotal = currentTotal.add(converted.amount());
            }
        }

        BigDecimal projected = currentTotal
                .multiply(BigDecimal.valueOf(daysInMonth))
                .divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);

        return new MonthlyForecast(new Money(projected, base), daysElapsed, daysInMonth);
    }

    @Override
    public List<BudgetStatus> budgetStatus(TransactionSet txs, List<Budget> budgets, Currency base) {
        List<BudgetStatus> statuses = new ArrayList<>();

        for (Budget budget : budgets) {
            BigDecimal spent = computeSpentForBudget(txs, budget);
            BigDecimal limit = budget.limit().amount();
            BigDecimal remaining = limit.subtract(spent).max(BigDecimal.ZERO);
            double percentUsed = limit.compareTo(BigDecimal.ZERO) > 0
                    ? spent.divide(limit, 4, RoundingMode.HALF_UP).doubleValue() * 100
                    : 0.0;

            statuses.add(new BudgetStatus(
                    budget,
                    new Money(spent, budget.limit().currency()),
                    new Money(remaining, budget.limit().currency()),
                    percentUsed
            ));
        }

        return statuses;
    }

    @Override
    public NetWorthTrend netWorthTrend(List<AccountBalance> balances) {
        Map<YearMonth, Money> monthlyNetWorth = new LinkedHashMap<>();

        // Group balances by month, sum across accounts
        Map<YearMonth, List<AccountBalance>> byMonth = balances.stream()
                .collect(Collectors.groupingBy(AccountBalance::month));

        for (Map.Entry<YearMonth, List<AccountBalance>> entry : byMonth.entrySet()) {
            Money total = entry.getValue().stream()
                    .map(ab -> converter.convert(ab.balance(), Currency.USD))
                    .reduce(Money::plus)
                    .orElse(new Money(BigDecimal.ZERO, Currency.USD));
            monthlyNetWorth.put(entry.getKey(), total);
        }

        return new NetWorthTrend(monthlyNetWorth);
    }

    @Override
    public MonthlySavingsRate monthlySavingsRate(TransactionSet txs, Currency base) {
        IncomeVsExpenses ive = incomeVsExpenses(txs, base);
        Map<YearMonth, BigDecimal> rates = new LinkedHashMap<>();

        for (Map.Entry<YearMonth, IncomeVsExpenses.MonthEntry> entry : ive.monthlyData().entrySet()) {
            BigDecimal income = entry.getValue().income().amount();
            BigDecimal expenses = entry.getValue().expenses().amount();

            if (income.compareTo(BigDecimal.ZERO) == 0) {
                // Undefined savings rate when income is zero
                rates.put(entry.getKey(), null);
            } else {
                BigDecimal rate = income.subtract(expenses)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(income, 2, RoundingMode.HALF_UP);
                rates.put(entry.getKey(), rate);
            }
        }

        return new MonthlySavingsRate(rates);
    }

    @Override
    public IncomeVsExpenses incomeVsExpenses(TransactionSet txs, Currency base) {
        Map<YearMonth, BigDecimal> incomeByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> expensesByMonth = new LinkedHashMap<>();

        for (Transaction tx : txs.transactions()) {
            if (tx.isInternalTransfer()) {
                continue;
            }
            YearMonth month = YearMonth.from(tx.date());
            Money converted = converter.convert(tx.amount(), base);

            if (tx.direction() == Direction.CREDIT) {
                incomeByMonth.merge(month, converted.amount(), BigDecimal::add);
            } else {
                expensesByMonth.merge(month, converted.amount(), BigDecimal::add);
            }
        }

        // Combine into result map
        Map<YearMonth, IncomeVsExpenses.MonthEntry> result = new LinkedHashMap<>();
        java.util.TreeSet<YearMonth> allMonths = new java.util.TreeSet<>();
        allMonths.addAll(incomeByMonth.keySet());
        allMonths.addAll(expensesByMonth.keySet());

        for (YearMonth month : allMonths) {
            Money income = new Money(
                    incomeByMonth.getOrDefault(month, BigDecimal.ZERO), base);
            Money expenses = new Money(
                    expensesByMonth.getOrDefault(month, BigDecimal.ZERO), base);
            result.put(month, new IncomeVsExpenses.MonthEntry(income, expenses));
        }

        return new IncomeVsExpenses(result);
    }

    private BigDecimal computeSpentForBudget(TransactionSet txs, Budget budget) {
        LocalDate start;
        LocalDate end;

        if (budget.period() instanceof BudgetPeriod.Monthly) {
            YearMonth current = YearMonth.now();
            start = current.atDay(1);
            end = current.atEndOfMonth();
        } else if (budget.period() instanceof BudgetPeriod.Custom(LocalDate s, LocalDate e)) {
            start = s;
            end = e;
        } else {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (Transaction tx : txs.transactions()) {
            if (tx.direction() != Direction.DEBIT
                    || !tx.category().orElse("").equalsIgnoreCase(budget.categoryName())
                    || tx.date().isBefore(start) || tx.date().isAfter(end)) {
                continue;
            }
            Money converted = converter.convert(tx.amount(), budget.limit().currency());
            total = total.add(converted.amount());
        }

        return total;
    }

    private Optional<RecurringPayment> findRecurringPattern(String merchantKey, List<Transaction> group) {
        List<Transaction> sorted = group.stream()
                .sorted(Comparator.comparing(Transaction::date))
                .toList();

        // Find subsequences with gaps of 25-35 days
        List<Long> gaps = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();

        for (int i = 1; i < sorted.size(); i++) {
            long daysBetween = ChronoUnit.DAYS.between(sorted.get(i - 1).date(), sorted.get(i).date());
            if (daysBetween >= 25 && daysBetween <= 35) {
                gaps.add(daysBetween);
                amounts.add(sorted.get(i).amount().amount());
                if (amounts.isEmpty()) {
                    amounts.add(sorted.get(i - 1).amount().amount());
                }
            }
        }

        if (gaps.size() < 2) {
            return Optional.empty();
        }

        // Check amount consistency (within ±10% of median)
        BigDecimal median = amounts.stream()
                .sorted()
                .skip(amounts.size() / 2)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        long consistentAmounts = amounts.stream()
                .filter(a -> isWithinPercent(a, median, 10))
                .count();

        if (consistentAmounts < amounts.size() * 0.7) {
            return Optional.empty();
        }

        int avgFrequency = (int) gaps.stream().mapToLong(Long::longValue).average().orElse(30);
        Currency currency = sorted.get(0).amount().currency();

        return Optional.of(new RecurringPayment(merchantKey, avgFrequency, new Money(median, currency)));
    }

    private boolean isWithinPercent(BigDecimal value, BigDecimal reference, int percent) {
        if (reference.compareTo(BigDecimal.ZERO) == 0) {
            return value.compareTo(BigDecimal.ZERO) == 0;
        }
        BigDecimal diff = value.subtract(reference).abs();
        BigDecimal threshold = reference.abs()
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return diff.compareTo(threshold) <= 0;
    }
}
