package com.pfa.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BudgetStatusTest {

    private Budget monthlyBudget(BigDecimal limit) {
        return new Budget(UUID.randomUUID(), "Food & Dining",
                new Money(limit, Currency.USD), new BudgetPeriod.Monthly());
    }

    private Budget customBudget(LocalDate start, LocalDate end, BigDecimal limit) {
        return new Budget(UUID.randomUUID(), "Travel",
                new Money(limit, Currency.USD), new BudgetPeriod.Custom(start, end));
    }

    @Test
    void isWarningReturnsTrueAt80Percent() {
        Budget budget = monthlyBudget(new BigDecimal("100.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("80.00"), Currency.USD),
                new Money(new BigDecimal("20.00"), Currency.USD),
                80.0);

        assertTrue(status.isWarning());
        assertFalse(status.isOverLimit());
    }

    @Test
    void isWarningReturnsTrueAt95Percent() {
        Budget budget = monthlyBudget(new BigDecimal("100.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("95.00"), Currency.USD),
                new Money(new BigDecimal("5.00"), Currency.USD),
                95.0);

        assertTrue(status.isWarning());
        assertFalse(status.isOverLimit());
    }

    @Test
    void isWarningReturnsFalseBelow80Percent() {
        Budget budget = monthlyBudget(new BigDecimal("100.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("79.99"), Currency.USD),
                new Money(new BigDecimal("20.01"), Currency.USD),
                79.99);

        assertFalse(status.isWarning());
        assertFalse(status.isOverLimit());
    }

    @Test
    void isOverLimitReturnsTrueAt100Percent() {
        Budget budget = monthlyBudget(new BigDecimal("100.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("100.00"), Currency.USD),
                new Money(new BigDecimal("0.00"), Currency.USD),
                100.0);

        assertTrue(status.isOverLimit());
        assertFalse(status.isWarning());
    }

    @Test
    void isOverLimitReturnsTrueAbove100Percent() {
        Budget budget = monthlyBudget(new BigDecimal("100.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("150.00"), Currency.USD),
                new Money(new BigDecimal("0.00"), Currency.USD),
                150.0);

        assertTrue(status.isOverLimit());
        assertFalse(status.isWarning());
    }

    @Test
    void monthlyBudgetIsNeverExpired() {
        Budget budget = monthlyBudget(new BigDecimal("500.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("200.00"), Currency.USD),
                new Money(new BigDecimal("300.00"), Currency.USD),
                40.0);

        assertFalse(status.isExpired());
    }

    @Test
    void customBudgetIsExpiredWhenEndDatePassed() {
        LocalDate pastStart = LocalDate.now().minusDays(60);
        LocalDate pastEnd = LocalDate.now().minusDays(1);
        Budget budget = customBudget(pastStart, pastEnd, new BigDecimal("1000.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("800.00"), Currency.USD),
                new Money(new BigDecimal("200.00"), Currency.USD),
                80.0);

        assertTrue(status.isExpired());
    }

    @Test
    void customBudgetIsNotExpiredWhenEndDateIsToday() {
        LocalDate start = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now();
        Budget budget = customBudget(start, end, new BigDecimal("1000.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("500.00"), Currency.USD),
                new Money(new BigDecimal("500.00"), Currency.USD),
                50.0);

        assertFalse(status.isExpired());
    }

    @Test
    void customBudgetIsNotExpiredWhenEndDateIsFuture() {
        LocalDate start = LocalDate.now().minusDays(10);
        LocalDate end = LocalDate.now().plusDays(20);
        Budget budget = customBudget(start, end, new BigDecimal("1000.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("300.00"), Currency.USD),
                new Money(new BigDecimal("700.00"), Currency.USD),
                30.0);

        assertFalse(status.isExpired());
    }

    @Test
    void normalStatusHasNoWarningOrOverLimit() {
        Budget budget = monthlyBudget(new BigDecimal("100.00"));
        BudgetStatus status = new BudgetStatus(budget,
                new Money(new BigDecimal("50.00"), Currency.USD),
                new Money(new BigDecimal("50.00"), Currency.USD),
                50.0);

        assertFalse(status.isWarning());
        assertFalse(status.isOverLimit());
        assertFalse(status.isExpired());
    }
}
