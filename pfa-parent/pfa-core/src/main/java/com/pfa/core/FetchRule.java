package com.pfa.core;

import java.util.Objects;
import java.util.Optional;

/**
 * A rule defining which Gmail messages to fetch attachments from.
 */
public record FetchRule(
        String senderPattern,
        String subjectPattern,
        Optional<DateRange> dateRange
) {

    public FetchRule {
        Objects.requireNonNull(senderPattern, "senderPattern must not be null");
        Objects.requireNonNull(subjectPattern, "subjectPattern must not be null");
        Objects.requireNonNull(dateRange, "dateRange must not be null");
    }
}
