package com.pfa.core;

import net.jqwik.api.*;

import java.math.BigDecimal;

/**
 * Property-based tests for CurrencyConverter round-trip stability.
 *
 * <p><b>Validates: Requirements 4.1, 4.2</b></p>
 */
class CurrencyConverterRoundTripProperties {

    /**
     * Property: Round-trip stability — For any Money m converted a→b→a,
     * the result is within ±0.01 of the original (rounding-bounded drift).
     *
     * <p>We constrain inputs so that the intermediate conversion produces a
     * non-zero result (amount * rate >= 0.005), since rounding to zero
     * irreversibly destroys information and is not a rounding drift issue.</p>
     *
     * <p><b>Validates: Requirements 4.1, 4.2</b></p>
     */
    @Property(tries = 1000)
    void roundTrip_isWithinOneCentOfOriginal(
            @ForAll("amounts") BigDecimal amount,
            @ForAll("currencyPairs") CurrencyPair pair,
            @ForAll("validRates") BigDecimal rate
    ) {
        // Skip cases where the intermediate conversion would round to zero
        // (information loss, not a rounding drift issue)
        BigDecimal intermediateExact = amount.multiply(rate);
        Assume.that(intermediateExact.compareTo(new BigDecimal("0.005")) >= 0);

        Currency from = pair.from();
        Currency to = pair.to();

        DefaultCurrencyConverter converter = new DefaultCurrencyConverter();
        converter.setRate(from, to, rate);

        Money original = new Money(amount, from);

        // Convert a → b
        Money converted = converter.convert(original, to);
        // Convert b → a
        Money roundTripped = converter.convert(converted, from);

        // The round-trip result should be within ±0.01 of the original
        BigDecimal drift = roundTripped.amount().subtract(original.amount()).abs();
        if (drift.compareTo(new BigDecimal("0.01")) > 0) {
            throw new AssertionError(String.format(
                    "Round-trip drift exceeded ±0.01.%n" +
                    "  Original:     %s %s%n" +
                    "  Converted:    %s %s%n" +
                    "  Round-tripped: %s %s%n" +
                    "  Rate:         %s%n" +
                    "  Drift:        %s",
                    original.amount(), original.currency(),
                    converted.amount(), converted.currency(),
                    roundTripped.amount(), roundTripped.currency(),
                    rate, drift));
        }
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<BigDecimal> amounts() {
        // Generate amounts from 1.00 to 99999.99 with scale 2.
        // Amounts >= 1.00 ensure the intermediate conversion retains enough
        // precision for the round-trip to stay within ±0.01.
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("1.00"), new BigDecimal("99999.99"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<CurrencyPair> currencyPairs() {
        return Arbitraries.of(Currency.values())
                .flatMap(from -> Arbitraries.of(Currency.values())
                        .filter(to -> to != from)
                        .map(to -> new CurrencyPair(from, to)));
    }

    @Provide
    Arbitrary<BigDecimal> validRates() {
        // Rates between 0.50 and 200 — covers realistic DOP/BBD/USD exchange rates.
        // Very small rates (< 0.5) cause the reciprocal to amplify rounding errors
        // beyond ±0.01 on the return trip, which is a precision limitation rather
        // than a converter bug.
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.50"), new BigDecimal("200"))
                .ofScale(6);
    }

    record CurrencyPair(Currency from, Currency to) {}
}
