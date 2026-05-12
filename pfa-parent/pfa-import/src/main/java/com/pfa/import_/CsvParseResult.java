package com.pfa.import_;

import java.util.List;
import java.util.Objects;

import com.pfa.core.RawTransaction;

/**
 * Result of a CSV parse attempt. Either parsing succeeded (with possible warnings for skipped rows),
 * or the format is unrecognized and column mapping is required from the user.
 */
public sealed interface CsvParseResult {

    /**
     * Parsing succeeded. Contains the extracted transactions and any warnings about skipped rows.
     */
    record Success(List<RawTransaction> transactions, List<String> warnings) implements CsvParseResult {
        public Success {
            Objects.requireNonNull(transactions, "transactions must not be null");
            Objects.requireNonNull(warnings, "warnings must not be null");
            transactions = List.copyOf(transactions);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * The CSV format could not be auto-detected. Contains the information needed for the UI
     * to present a column mapping interface.
     */
    record MappingRequired(List<String> headers, List<List<String>> sampleRows, char detectedDelimiter)
            implements CsvParseResult {
        public MappingRequired {
            Objects.requireNonNull(headers, "headers must not be null");
            Objects.requireNonNull(sampleRows, "sampleRows must not be null");
            headers = List.copyOf(headers);
            sampleRows = List.copyOf(sampleRows);
        }
    }

    /**
     * The CSV file is empty or contains no data rows.
     */
    record Empty(String reason) implements CsvParseResult {
        public Empty {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /**
     * The file could not be read due to encoding issues or other I/O errors.
     */
    record Error(String message) implements CsvParseResult {
        public Error {
            Objects.requireNonNull(message, "message must not be null");
        }
    }
}
