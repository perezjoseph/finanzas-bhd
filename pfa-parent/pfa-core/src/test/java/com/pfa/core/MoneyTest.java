package com.pfa.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void compactConstructorEnforcesScale2() {
        Money m = new Money(new BigDecimal("10.999"), Currency.USD);
        assertEquals(new BigDecimal("11.00"), m.amount());
    }

    @Test
    void compactConstructorRoundsHalfUp() {
        Money m = new Money(new BigDecimal("1.005"), Currency.DOP);
        assertEquals(new BigDecimal("1.01"), m.amount());
    }

    @Test
    void plusSameCurrency() {
        Money a = new Money(new BigDecimal("10.50"), Currency.USD);
        Money b = new Money(new BigDecimal("3.25"), Currency.USD);
        Money result = a.plus(b);
        assertEquals(new BigDecimal("13.75"), result.amount());
        assertEquals(Currency.USD, result.currency());
    }

    @Test
    void plusDifferentCurrencyThrows() {
        Money a = new Money(new BigDecimal("10.00"), Currency.USD);
        Money b = new Money(new BigDecimal("5.00"), Currency.DOP);
        assertThrows(IllegalArgumentException.class, () -> a.plus(b));
    }

    @Test
    void timesMultipliesAndScales() {
        Money m = new Money(new BigDecimal("10.00"), Currency.BBD);
        Money result = m.times(new BigDecimal("3"));
        assertEquals(new BigDecimal("30.00"), result.amount());
        assertEquals(Currency.BBD, result.currency());
    }

    @Test
    void timesWithFractionalFactor() {
        Money m = new Money(new BigDecimal("100.00"), Currency.USD);
        Money result = m.times(new BigDecimal("0.333"));
        assertEquals(new BigDecimal("33.30"), result.amount());
    }

    @Test
    void nullAmountThrows() {
        assertThrows(NullPointerException.class, () -> new Money(null, Currency.USD));
    }

    @Test
    void nullCurrencyThrows() {
        assertThrows(NullPointerException.class, () -> new Money(BigDecimal.TEN, null));
    }

    @Test
    void plusNullThrows() {
        Money m = new Money(BigDecimal.ONE, Currency.USD);
        assertThrows(NullPointerException.class, () -> m.plus(null));
    }

    @Test
    void timesNullThrows() {
        Money m = new Money(BigDecimal.ONE, Currency.USD);
        assertThrows(NullPointerException.class, () -> m.times(null));
    }
}
