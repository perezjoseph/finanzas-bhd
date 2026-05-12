package com.pfa.core;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated result of importing a batch of statement files.
 */
public record ImportBatchResult(
        List<Transaction> successes,
        List<ImportError> failures,
        List<ImportWarning> warnings,
        List<String> duplicates,
        List<String> emptyFiles
) {

    public ImportBatchResult {
        Objects.requireNonNull(successes, "successes must not be null");
        Objects.requireNonNull(failures, "failures must not be null");
        Objects.requireNonNull(warnings, "warnings must not be null");
        Objects.requireNonNull(duplicates, "duplicates must not be null");
        Objects.requireNonNull(emptyFiles, "emptyFiles must not be null");

        successes = List.copyOf(successes);
        failures = List.copyOf(failures);
        warnings = List.copyOf(warnings);
        duplicates = List.copyOf(duplicates);
        emptyFiles = List.copyOf(emptyFiles);
    }
}
