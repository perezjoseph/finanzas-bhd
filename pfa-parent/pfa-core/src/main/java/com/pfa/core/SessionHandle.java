package com.pfa.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Metadata for a saved session file. Used to list and identify sessions
 * without loading their full contents.
 */
public record SessionHandle(
        String name,
        Path file,
        String schemaVersion,
        Instant savedAt
) {

    public SessionHandle {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(savedAt, "savedAt must not be null");
    }
}
