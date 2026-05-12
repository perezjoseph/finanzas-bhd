package com.pfa.core;

import net.jqwik.api.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Property-based tests for CurrencyConverter reciprocal consistency.
 *
 * <p><b>Validates: Requirements 4.1, 4.3</b></p>
 *
 * <p>Property: For any rate r set for pair (a, b), rate(a, b) * rate(b, a) ≈ 1
 * within rounding tolerance.</p>
 */
class CurrencyConverterReciprocalProperties {

    private static final BigDecimal TOLERANCE = new BigDecimal("1E-9");

    @Property
    void reciprocalConsistency_rateProductApproximatesOne(
            @ForAll("validRates") BigDecimal rate,
            @ForAll("distinctCurrencyPairs") CurrencyPair pair
    ) {
        DefaultCurrencyConverter converter = new DefaultCurrencyConverter();
        converter.setRate(pair.from(), pair.to(), rate);

        BigDecimal forward = converter.rate(pair.from(), pair.to());
        BigDecimal inverse = converter.rate(pair.to(), pair.from());
        BigDecimal product = forward.multiply(inverse, new MathContext(18, RoundingMode.HALF_UP));

        BigDecimal deviation = product.subtract(BigDecimal.ONE).abs();
        Assertions.assertThat(deviation)
                .as("rate(%s,%s) * rate(%s,%s) should ≈ 1, got product=%s (deviation=%s) for rate=%s",
                        pair.from(), pair.to(), pair.to(), pair.from(), product, deviation, rate)
                .isLessThan(TOLERANCE);
    }

    @Provide
    Arbitrary<BigDecimal> validRates() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.000001"), new BigDecimal("999999"))
                .ofScale(6)
                .filter(bd -> bd.compareTo(BigDecimal.ZERO) > 0);
    }

    @Provide
    Arbitrary<CurrencyPair> distinctCurrencyPairs() {
        return Arbitraries.of(Currency.values())
                .tuple2()
                .filter(t -> t.get1() != t.get2())
                .map(t -> new CurrencyPair(t.get1(), t.get2()));
    }

    record CurrencyPair(Currency from, Currency to) {}

    /**
     * Minimal assertion helper to avoid pulling in AssertJ as a dependency.
     */
    private static class Assertions {
        static BigDecimalAssert assertThat(BigDecimal actual) {
            return new BigDecimalAssert(actual);
        }

        static class BigDecimalAssert {
            private final BigDecimal actual;
            private String message = "";

            BigDecimalAssert(BigDecimal actual) {
                this.actual = actual;
            }

            BigDecimalAssert as(String format, Object... args) {
                this.message = String.format(format, args);
                return this;
            }

            void isLessThan(BigDecimal expected) {
                if (actual.compareTo(expected) >= 0) {
                    throw new AssertionError(message.isEmpty()
                            ? "Expected " + actual + " to be less than " + expected
                            : message);
                }
            }
        }
    }
}
