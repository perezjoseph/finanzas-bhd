package com.pfa.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link CurrencyConverter}.
 * <p>
 * Stores rates in a map keyed by (from, to) currency pairs.
 * Enforces reciprocal consistency, supports triangulation through
 * an intermediate currency, and uses BigDecimal with MathContext(18, HALF_UP)
 * for all intermediate calculations.
 */
public class DefaultCurrencyConverter implements CurrencyConverter {

    private static final MathContext MC = new MathContext(18, RoundingMode.HALF_UP);
    private static final BigDecimal MAX_RATE = new BigDecimal("999999");

    private final Map<CurrencyPair, BigDecimal> rates = new ConcurrentHashMap<>();

    @Override
    public Money convert(Money source, Currency target) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");

        if (source.currency() == target) {
            return source;
        }

        BigDecimal exchangeRate = rate(source.currency(), target);
        BigDecimal converted = source.amount().multiply(exchangeRate, MC);
        return new Money(converted.setScale(2, RoundingMode.HALF_UP), target);
    }

    @Override
    public BigDecimal rate(Currency from, Currency to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");

        if (from == to) {
            return BigDecimal.ONE;
        }

        // Try direct lookup
        BigDecimal direct = rates.get(new CurrencyPair(from, to));
        if (direct != null) {
            return direct;
        }

        // Try triangulation through each possible intermediate currency
        for (Currency intermediate : Currency.values()) {
            if (intermediate == from || intermediate == to) {
                continue;
            }
            BigDecimal rateFromToIntermediate = rates.get(new CurrencyPair(from, intermediate));
            BigDecimal rateIntermediateTo = rates.get(new CurrencyPair(intermediate, to));
            if (rateFromToIntermediate != null && rateIntermediateTo != null) {
                return rateFromToIntermediate.multiply(rateIntermediateTo, MC);
            }
        }

        throw new MissingRateException(from, to);
    }

    @Override
    public void setRate(Currency from, Currency to, BigDecimal rate) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(rate, "rate must not be null");

        if (from == to) {
            throw new IllegalArgumentException("Cannot set rate for same currency: " + from);
        }
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Rate must be > 0, got: " + rate);
        }
        if (rate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException("Rate must be <= 999,999, got: " + rate);
        }

        rates.put(new CurrencyPair(from, to), rate);
        BigDecimal reciprocal = BigDecimal.ONE.divide(rate, MC);
        rates.put(new CurrencyPair(to, from), reciprocal);
    }

    /**
     * Internal key for the rate store.
     */
    private record CurrencyPair(Currency from, Currency to) {}
}
