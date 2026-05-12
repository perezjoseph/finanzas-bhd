package com.pfa.core;

import net.jqwik.api.*;

import java.math.BigDecimal;

/**
 * Property-based tests for CurrencyConverter triangulation correctness.
 *
 * <p><b>Validates: Requirements 4.1, 4.6</b></p>
 */
class CurrencyConverterTriangulationProperties {

    /**
     * Property: Triangulation correctness — If rates (a,b) and (b,c) are set,
     * then convert(m, c) via the direct derived (triangulated) rate equals
     * sequential conversion convert(convert(m, b), c) within rounding tolerance.
     *
     * <p>The tolerance accounts for the intermediate rounding in the sequential path:
     * the a→b conversion rounds to 2 decimal places, introducing up to 0.005 error
     * which is then amplified by rateBC. Total tolerance = 0.005 * rateBC + 0.005.</p>
     *
     * <p><b>Validates: Requirements 4.1, 4.6</b></p>
     */
    @Property
    void triangulatedConversion_matchesSequentialConversion(
            @ForAll("distinctCurrencyTriple") CurrencyTriple triple,
            @ForAll("validRate") BigDecimal rateAB,
            @ForAll("validRate") BigDecimal rateBC,
            @ForAll("positiveAmount") BigDecimal amount) {

        Currency a = triple.a();
        Currency b = triple.b();
        Currency c = triple.c();

        // Set up converter with only (a,b) and (b,c) rates — NOT (a,c) directly
        DefaultCurrencyConverter converter = new DefaultCurrencyConverter();
        converter.setRate(a, b, rateAB);
        converter.setRate(b, c, rateBC);

        Money moneyInA = new Money(amount, a);

        // Single-step triangulated conversion: a → c (uses internal triangulation)
        Money directResult = converter.convert(moneyInA, c);

        // Two-step sequential conversion: a → b → c
        Money intermediate = converter.convert(moneyInA, b);
        Money sequentialResult = converter.convert(intermediate, c);

        // Tolerance accounts for intermediate rounding: the a→b step rounds to 2dp,
        // introducing up to 0.005 error, which is then multiplied by rateBC.
        // Each path also has its own final rounding (up to 0.005 each), so the two
        // final results can differ by up to 0.005 * rateBC + 0.01.
        BigDecimal halfCent = new BigDecimal("0.005");
        BigDecimal oneCent = new BigDecimal("0.01");
        BigDecimal tolerance = halfCent.multiply(rateBC).add(oneCent);

        BigDecimal difference = directResult.amount().subtract(sequentialResult.amount()).abs();
        assert difference.compareTo(tolerance) <= 0
                : "Triangulated conversion (" + directResult.amount()
                + ") and sequential conversion (" + sequentialResult.amount()
                + ") differ by " + difference + ", exceeding tolerance " + tolerance;
    }

    @Provide
    Arbitrary<CurrencyTriple> distinctCurrencyTriple() {
        return Arbitraries.of(
                new CurrencyTriple(Currency.DOP, Currency.USD, Currency.BBD),
                new CurrencyTriple(Currency.DOP, Currency.BBD, Currency.USD),
                new CurrencyTriple(Currency.USD, Currency.DOP, Currency.BBD),
                new CurrencyTriple(Currency.USD, Currency.BBD, Currency.DOP),
                new CurrencyTriple(Currency.BBD, Currency.DOP, Currency.USD),
                new CurrencyTriple(Currency.BBD, Currency.USD, Currency.DOP)
        );
    }

    @Provide
    Arbitrary<BigDecimal> validRate() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("999999"))
                .ofScale(4);
    }

    @Provide
    Arbitrary<BigDecimal> positiveAmount() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("999999.99"))
                .ofScale(2);
    }

    record CurrencyTriple(Currency a, Currency b, Currency c) {}
}
