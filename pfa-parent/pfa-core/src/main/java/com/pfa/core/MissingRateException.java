package com.pfa.core;

/**
 * Thrown when a currency conversion is requested but the required rate
 * cannot be found or derived via triangulation.
 */
public class MissingRateException extends RuntimeException {

    private final Currency from;
    private final Currency to;

    public MissingRateException(Currency from, Currency to) {
        super("No exchange rate available for " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public Currency getFrom() {
        return from;
    }

    public Currency getTo() {
        return to;
    }
}
