package com.pfa.core;

import java.util.List;
import java.util.Objects;

/**
 * Sealed result type for pipeline operations. Errors never throw across module boundaries —
 * they are captured in this type and propagated structurally.
 *
 * @param <T> the type of the successful value
 */
public sealed interface Result<T> {

    /**
     * The operation completed successfully with a value.
     */
    record Ok<T>(T value) implements Result<T> {
        public Ok {
            Objects.requireNonNull(value, "value must not be null");
        }
    }

    /**
     * The operation failed with an error.
     */
    record Err<T>(ImportError error) implements Result<T> {
        public Err {
            Objects.requireNonNull(error, "error must not be null");
        }
    }

    /**
     * The operation produced a value but with warnings that the user should review.
     */
    record Partial<T>(T value, List<ImportWarning> warnings) implements Result<T> {
        public Partial {
            Objects.requireNonNull(value, "value must not be null");
            Objects.requireNonNull(warnings, "warnings must not be null");
            warnings = List.copyOf(warnings); // defensive immutable copy
        }
    }
}
