package com.pfa.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCurrencyConverterTest {

    private DefaultCurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DefaultCurrencyConverter();
    }

    // --- Identity ---

    @Test
    void convert_sameCurrency_returnsIdentical() {
        Money usd = new Money(new BigDecimal("100.50"), Currency.USD);
        Money result = converter.convert(usd, Currency.USD);
        assertEquals(usd, result);
    }

    @Test
    void convert_sameCurrency_zeroAmount() {
        Money zero = new Money(BigDecimal.ZERO, Currency.DOP);
        assertEquals(zero, converter.convert(zero, Currency.DOP));
    }

    // --- setRate validation ---

    @Test
    void setRate_zeroRate_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.setRate(Currency.USD, Currency.DOP, BigDecimal.ZERO));
    }

    @Test
    void setRate_negativeRate_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("-1")));
    }

    @Test
    void setRate_exceedsMax_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("999999.01")));
    }

    @Test
    void setRate_exactMax_succeeds() {
        assertDoesNotThrow(
                () -> converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("999999")));
    }

    @Test
    void setRate_sameCurrency_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.setRate(Currency.USD, Currency.USD, BigDecimal.ONE));
    }

    @Test
    void setRate_nullArgs_throws() {
        assertThrows(NullPointerException.class,
                () -> converter.setRate(null, Currency.USD, BigDecimal.ONE));
        assertThrows(NullPointerException.class,
                () -> converter.setRate(Currency.USD, null, BigDecimal.ONE));
        assertThrows(NullPointerException.class,
                () -> converter.setRate(Currency.USD, Currency.DOP, null));
    }

    // --- Reciprocal consistency ---

    @Test
    void setRate_storesReciprocal() {
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("57.50"));

        BigDecimal forward = converter.rate(Currency.USD, Currency.DOP);
        BigDecimal inverse = converter.rate(Currency.DOP, Currency.USD);

        assertEquals(0, new BigDecimal("57.50").compareTo(forward));
        // reciprocal: 1/57.50 ≈ 0.0173913...
        BigDecimal product = forward.multiply(inverse);
        // product should be ~1 within rounding
        assertTrue(product.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.000000001")) < 0,
                "forward * inverse should be ~1, got: " + product);
    }

    // --- Direct conversion ---

    @Test
    void convert_directRate() {
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("57.50"));

        Money usd = new Money(new BigDecimal("100.00"), Currency.USD);
        Money result = converter.convert(usd, Currency.DOP);

        assertEquals(Currency.DOP, result.currency());
        assertEquals(0, new BigDecimal("5750.00").compareTo(result.amount()));
    }

    @Test
    void convert_inverseRate() {
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("57.50"));

        Money dop = new Money(new BigDecimal("5750.00"), Currency.DOP);
        Money result = converter.convert(dop, Currency.USD);

        assertEquals(Currency.USD, result.currency());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.amount()));
    }

    // --- Triangulation ---

    @Test
    void rate_triangulation_derivesIndirectRate() {
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("57.50"));
        converter.setRate(Currency.USD, Currency.BBD, new BigDecimal("2.00"));

        // DOP -> BBD should be derived via USD: DOP->USD * USD->BBD
        BigDecimal dopToBbd = converter.rate(Currency.DOP, Currency.BBD);
        // DOP->USD = 1/57.50, USD->BBD = 2.00 => DOP->BBD = 2/57.50 ≈ 0.0347826...
        BigDecimal expected = new BigDecimal("2").divide(new BigDecimal("57.50"),
                18, java.math.RoundingMode.HALF_UP);
        assertTrue(dopToBbd.subtract(expected).abs().compareTo(new BigDecimal("0.000000001")) < 0,
                "Expected ~" + expected + " but got " + dopToBbd);
    }

    @Test
    void convert_triangulation() {
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("57.50"));
        converter.setRate(Currency.USD, Currency.BBD, new BigDecimal("2.00"));

        Money dop = new Money(new BigDecimal("5750.00"), Currency.DOP);
        Money result = converter.convert(dop, Currency.BBD);

        assertEquals(Currency.BBD, result.currency());
        // 5750 DOP * (1/57.50) USD/DOP * 2 BBD/USD = 200 BBD
        assertEquals(0, new BigDecimal("200.00").compareTo(result.amount()));
    }

    // --- MissingRateException ---

    @Test
    void rate_noRateSet_throws() {
        assertThrows(MissingRateException.class,
                () -> converter.rate(Currency.USD, Currency.DOP));
    }

    @Test
    void rate_noTriangulationPossible_throws() {
        // Only set USD->DOP, ask for BBD->DOP (no path through any intermediate)
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("57.50"));
        assertThrows(MissingRateException.class,
                () -> converter.rate(Currency.BBD, Currency.DOP));
    }

    @Test
    void convert_missingRate_throws() {
        Money m = new Money(new BigDecimal("100"), Currency.BBD);
        assertThrows(MissingRateException.class,
                () -> converter.convert(m, Currency.DOP));
    }

    // --- rate(a, a) returns 1 ---

    @Test
    void rate_sameCurrency_returnsOne() {
        assertEquals(0, BigDecimal.ONE.compareTo(converter.rate(Currency.USD, Currency.USD)));
    }

    // --- Overwriting rates ---

    @Test
    void setRate_overwrite_updatesRate() {
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("57.50"));
        converter.setRate(Currency.USD, Currency.DOP, new BigDecimal("60.00"));

        assertEquals(0, new BigDecimal("60.00").compareTo(converter.rate(Currency.USD, Currency.DOP)));
        // Reciprocal also updated
        BigDecimal inverse = converter.rate(Currency.DOP, Currency.USD);
        BigDecimal expected = BigDecimal.ONE.divide(new BigDecimal("60.00"), 18, java.math.RoundingMode.HALF_UP);
        assertTrue(inverse.subtract(expected).abs().compareTo(new BigDecimal("0.000000001")) < 0);
    }

    // --- Rounding to 2 decimals ---

    @Test
    void convert_roundsToTwoDecimals() {
        // 1/3 rate produces repeating decimal
        converter.setRate(Currency.USD, Currency.BBD, new BigDecimal("3"));

        Money usd = new Money(new BigDecimal("1.00"), Currency.USD);
        Money result = converter.convert(usd, Currency.BBD);

        assertEquals(2, result.amount().scale());
        assertEquals(0, new BigDecimal("3.00").compareTo(result.amount()));
    }

    @Test
    void convert_inverseRoundsToTwoDecimals() {
        converter.setRate(Currency.USD, Currency.BBD, new BigDecimal("3"));

        Money bbd = new Money(new BigDecimal("1.00"), Currency.BBD);
        Money result = converter.convert(bbd, Currency.USD);

        assertEquals(2, result.amount().scale());
        // 1/3 = 0.333... rounds to 0.33
        assertEquals(0, new BigDecimal("0.33").compareTo(result.amount()));
    }
}
