package com.pfa.core;

/**
 * Warnings emitted during import that do not prevent transaction extraction
 * but indicate potential data quality issues the user should review.
 */
public sealed interface ImportWarning {

    /**
     * The computed balance from transactions does not match the statement's declared balance.
     *
     * @param details description of the mismatch (e.g., expected vs actual values)
     */
    record BalanceMismatch(String details) implements ImportWarning {}

    /**
     * A transaction is missing one or more optional fields that could not be extracted.
     *
     * @param transactionId   identifier or index of the affected transaction
     * @param missingField    name of the field that could not be extracted
     */
    record IncompleteTransaction(String transactionId, String missingField) implements ImportWarning {}

    /**
     * A section total in the statement does not match the sum of individual transactions.
     *
     * @param section  name of the section (e.g., "TRANSACCIONES EN DOLARES US$")
     * @param expected the declared total
     * @param actual   the computed total from individual transactions
     */
    record SectionTotalMismatch(String section, String expected, String actual) implements ImportWarning {}

    /**
     * OCR confidence for one or more pages fell below the acceptable threshold.
     *
     * @param pageNumber        the page with low confidence
     * @param confidencePercent the OCR confidence as a percentage (0–100)
     */
    record LowOcrConfidence(int pageNumber, double confidencePercent) implements ImportWarning {}
}
