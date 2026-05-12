package com.pfa.core;

import net.jqwik.api.*;

import java.math.BigDecimal;

/**
 * Property-based tests for CurrencyConverter identity conversion.
 *
 * <p><b>Validates: Requirements 4.1, 4.2</b></p>
 */
class CurrencyConverterIdentityProperties {

    private final DefaultCurrencyConverter converter = new DefaultCurrencyConverter();

    /**
     * Property: Identity conversion — For any Money m, convert(m, m.currency()) equals m exactly.
     *
     * <p><b>Validates: Requirements 4.1, 4.2</b></p>
     */
    @Property
    void identityConversion_returnsExactSameMoneyForAnyCurrency(
            @ForAll("arbitraryMoney") Money money) {

        Money result = converter.convert(money, money.currency());

        // Same amount (exact equality after scale normalization)
        assert result.amount().compareTo(money.amount()) == 0
                : "Expected amount " + money.amount() + " but got " + result.amount();

        // Same currency
        assert result.currency() == money.currency()
                : "Expected currency " + money.currency() + " but got " + result.currency();
    }

    @Provide
    Arbitrary<Money> arbitraryMoney() {
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals()
                .between(new BigDecimal("-999999999.99"), new BigDecimal("999999999.99"))
                .ofScale(2);

        Arbitrary<Currency> currencies = Arbitraries.of(Currency.values());

        return Combinators.combine(amounts, currencies)
                .as(Money::new);
    }
}
