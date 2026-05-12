package com.pfa.core;

/**
 * Converts a raw transaction into the canonical Transaction record,
 * applying normalization rules (date formatting, description cleanup,
 * amount/direction derivation). Never converts currency.
 */
public interface TransactionNormalizer {

    /**
     * Normalizes a raw transaction into a canonical Transaction with any detected field issues.
     */
    NormalizedTransaction normalize(RawTransaction raw, AccountAssignment account, FormatDescriptor format);
}
