package com.pfa.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pfa.core.SessionHandle;
import com.pfa.core.SessionSnapshot;

/**
 * Tests for DefaultSessionManager focusing on atomic save, schema version checking,
 * and corruption handling.
 * Validates: Requirements 10.1, 10.2, 10.3
 */
class DefaultSessionManagerTest {

    @TempDir
    Path tempDir;

    private DefaultSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new DefaultSessionManager(tempDir);
    }

    @Test
    void saveCreatesFileAtomically() throws IOException {
        SessionSnapshot snapshot = emptySnapshot("1.0.0");

        SessionHandle handle = sessionManager.save("atomic-test", snapshot);

        assertTrue(Files.exists(handle.file()));
        // No temp file should remain
        long tempFiles = Files.list(handle.file().getParent())
                .filter(p -> p.toString().endsWith(".tmp"))
                .count();
        assertEquals(0, tempFiles, "No temp files should remain after successful save");
    }

    @Test
    void loadCorruptedFileThrowsWithCorruptionMessage() throws IOException {
        // Save a valid session first to get the file path
        SessionHandle handle = sessionManager.save("corrupt-test", emptySnapshot("1.0.0"));

        // Corrupt the file
        Files.writeString(handle.file(), "this is not valid JSON at all {{{");

        // Load should throw indicating corruption
        UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                () -> sessionManager.load(handle));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("corrupt-test") || ex.getCause().getMessage().contains("parse"),
                "Error should indicate the nature of the failure");
    }

    @Test
    void loadIncompatibleSchemaVersionThrows() throws IOException {
        // Save a session with a future major version (incompatible)
        SessionSnapshot futureSnapshot = emptySnapshot("2.0.0");
        // Serialize directly to bypass the manager's version check on save
        SnapshotSerializer serializer = new SnapshotSerializer();
        byte[] data = serializer.serialize(futureSnapshot);

        // Write it manually to the sessions directory
        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Path sessionFile = sessionsDir.resolve("future-session.pfa");
        Files.write(sessionFile, data);

        SessionHandle handle = new SessionHandle("future-session", sessionFile, "2.0.0", Instant.now());

        // Load should throw indicating version mismatch
        UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                () -> sessionManager.load(handle));
        assertTrue(ex.getCause().getMessage().contains("Incompatible session schema version")
                        || ex.getCause().getMessage().contains("version"),
                "Error should indicate version mismatch");
    }

    @Test
    void loadCompatibleMinorVersionSucceeds() throws IOException {
        // Save a session with same major but different minor version (compatible)
        SessionSnapshot snapshot = emptySnapshot("1.2.0");
        SnapshotSerializer serializer = new SnapshotSerializer();
        byte[] data = serializer.serialize(snapshot);

        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Path sessionFile = sessionsDir.resolve("minor-version.pfa");
        Files.write(sessionFile, data);

        SessionHandle handle = new SessionHandle("minor-version", sessionFile, "1.2.0", Instant.now());

        // Should load successfully — same major version is compatible
        SessionSnapshot loaded = sessionManager.load(handle);
        assertEquals("1.2.0", loaded.schemaVersion());
    }

    @Test
    void loadNonExistentFileThrows() {
        Path nonExistent = tempDir.resolve("sessions").resolve("ghost.pfa");
        SessionHandle handle = new SessionHandle("ghost", nonExistent, "1.0.0", Instant.now());

        assertThrows(UncheckedIOException.class, () -> sessionManager.load(handle));
    }

    @Test
    void saveOverwriteReplacesExistingFile() throws IOException {
        SessionSnapshot first = emptySnapshot("1.0.0");
        SessionHandle handle1 = sessionManager.save("overwrite-me", first);
        long firstSize = Files.size(handle1.file());

        // Save again with settings to make it larger
        SessionSnapshot second = new SessionSnapshot("1.0.0", List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                Map.of("key1", "value1", "key2", "value2", "key3", "value3"));
        SessionHandle handle2 = sessionManager.save("overwrite-me", second);

        // File should be updated (different size due to settings)
        long secondSize = Files.size(handle2.file());
        assertTrue(secondSize > firstSize, "Overwritten file should be larger with more settings");

        // Only one session file should exist
        List<SessionHandle> sessions = sessionManager.list();
        assertEquals(1, sessions.size());
    }

    @Test
    void loadEmptyFileThrows() throws IOException {
        Path sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(sessionsDir);
        Path emptyFile = sessionsDir.resolve("empty.pfa");
        Files.writeString(emptyFile, "");

        SessionHandle handle = new SessionHandle("empty", emptyFile, "1.0.0", Instant.now());

        assertThrows(UncheckedIOException.class, () -> sessionManager.load(handle));
    }

    private SessionSnapshot emptySnapshot(String version) {
        return new SessionSnapshot(version, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of());
    }
}
