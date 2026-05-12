package com.pfa.core;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A source from which statements can be imported.
 */
public sealed interface ImportSource {

    /**
     * A local file on disk (PDF or CSV).
     */
    record LocalFile(Path path, AccountAssignment account) implements ImportSource {
        public LocalFile {
            Objects.requireNonNull(path, "path must not be null");
            Objects.requireNonNull(account, "account must not be null");
        }
    }

    /**
     * A PDF attachment fetched from Gmail.
     */
    record GmailAttachment(byte[] bytes, String filename, AccountAssignment account) implements ImportSource {
        public GmailAttachment {
            Objects.requireNonNull(bytes, "bytes must not be null");
            Objects.requireNonNull(filename, "filename must not be null");
            Objects.requireNonNull(account, "account must not be null");
        }
    }
}
