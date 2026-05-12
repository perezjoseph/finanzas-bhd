package com.pfa.core;

import java.util.List;
import java.util.Objects;

/**
 * Result of fetching statement attachments from Gmail.
 */
public record FetchResult(List<ImportSource> downloaded, List<String> errors) {

    public FetchResult {
        Objects.requireNonNull(downloaded, "downloaded must not be null");
        Objects.requireNonNull(errors, "errors must not be null");
        downloaded = List.copyOf(downloaded);
        errors = List.copyOf(errors);
    }
}
