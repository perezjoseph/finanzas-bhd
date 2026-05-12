package com.pfa.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyTest {

    @Test
    void dopHasCorrectSymbol() {
        assertEquals("RD$", Currency.DOP.symbol);
    }

    @Test
    void bbdHasCorrectSymbol() {
        assertEquals("BBD$", Currency.BBD.symbol);
    }

    @Test
    void usdHasCorrectSymbol() {
        assertEquals("US$", Currency.USD.symbol);
    }

    @Test
    void allThreeCurrenciesExist() {
        assertEquals(3, Currency.values().length);
    }
}
