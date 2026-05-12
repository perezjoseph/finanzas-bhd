package com.pfa.app;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.pfa.core.Currency;
import com.pfa.core.DefaultCurrencyConverter;
import com.pfa.core.MissingRateException;

/**
 * Tests for exchange rate saving logic as wired in AppController.wireSettingsView().
 * Validates: Requirements 4.3, 4.5
 *
 * The AppController reads text fields, validates (> 0, ≤ 999,999), and calls
 * facade.setExchangeRate() for each pair. These tests verify the underlying
 * behavior that the controller relies on.
 */
class ExchangeRateSavingTest {

    private DefaultCurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DefaultCurrencyConverter();
    }

    @Test
    void validRatesArePersisted() {
        BigDecimal usdToDop = new BigDecimal("58.50");
        BigDecimal usdToBbd = new BigDecimal("2.00");
        BigDecimal dopToBbd = new BigDecimal("0.034");

        converter.setRate(Currency.USD, Currency.DOP, usdToDop);
        converter.setRate(Currency.USD, Currency.BBD, usdToBbd);
        converter.setRate(Currency.DOP, Currency.BBD, dopToBbd);

        assertEquals(usdToDop, converter.rate(Currency.USD, Currency.DOP));
        assertEquals(usdToBbd, converter.rate(Currency.USD, Currency.BBD));
        assertEquals(dopToBbd, converter.rate(Currency.DOP, Currency.BBD));
    }

    @Test
    void zeroRateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.setRate(Currency.USD, Currency.DOP, BigDecimal.ZERO));
    }

    @Test
    void negativeRateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("-1.5")));
    }

    @Test
    void rateExceeding999999IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("999999.01")));
    }

    @Test
    void rateAtExactly999999IsAccepted() {
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("999999"));
        assertEquals(new BigDecimal("999999"), converter.rate(Currency.USD, Currency.DOP));
    }

    @Test
    void rateJustAboveZeroIsAccepted() {
        BigDecimal smallRate = new BigDecimal("0.0001");
        converter.setRate(Currency.USD, Currency.DOP, smallRate);
        assertEquals(smallRate, converter.rate(Currency.USD, Currency.DOP));
    }

    @Test
    void settingRateUpdatesSubsequentConversions() {
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("58.50"));

        // Update the rate
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("60.00"));

        assertEquals(new BigDecimal("60.00"), converter.rate(Currency.USD, Currency.DOP));
    }

    @Test
    void reciprocalIsAutomaticallySet() {
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("58.50"));

        // DOP to USD should be the reciprocal
        BigDecimal dopToUsd = converter.rate(Currency.DOP, Currency.USD);
        assertNotNull(dopToUsd);
        // 1/58.50 ≈ 0.017094...
        assertTrue(dopToUsd.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(dopToUsd.compareTo(BigDecimal.ONE) < 0);
    }

    @Test
    void missingRateThrowsException() {
        // No rates set — should throw MissingRateException
        assertThrows(MissingRateException.class,
                () -> converter.rate(Currency.USD, Currency.DOP));
    }

    @Test
    void allThreePairsCanBeSetIndependently() {
        // Simulates what AppController does: set all three pairs
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("58.50"));
        converter.setRate(Currency.USD, Currency.BBD, new BigDecimal("2.00"));
        converter.setRate(Currency.DOP, Currency.BBD, new BigDecimal("0.034"));

        // All direct lookups work
        assertDoesNotThrow(() -> converter.rate(Currency.USD, Currency.DOP));
        assertDoesNotThrow(() -> converter.rate(Currency.USD, Currency.BBD));
        assertDoesNotThrow(() -> converter.rate(Currency.DOP, Currency.BBD));

        // Reciprocals also work
        assertDoesNotThrow(() -> converter.rate(Currency.DOP, Currency.USD));
        assertDoesNotThrow(() -> converter.rate(Currency.BBD, Currency.USD));
        assertDoesNotThrow(() -> converter.rate(Currency.BBD, Currency.DOP));
    }
}
