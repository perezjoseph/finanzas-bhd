package com.pfa.core;

/**
 * Supported currencies for the Personal Finance Analyzer.
 */
public enum Currency {
    DOP("RD$"),
    BBD("BBD$"),
    USD("US$");

    public final String symbol;

    Currency(String symbol) {
        this.symbol = symbol;
    }
}
