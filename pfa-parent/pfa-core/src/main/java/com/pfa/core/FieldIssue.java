package com.pfa.core;

/**
 * Issues detected during transaction normalization that flag a transaction for user review.
 */
public enum FieldIssue {
    MissingRequired,
    UnsupportedCurrency,
    AmountParseFailed,
    DateParseFailed
}
