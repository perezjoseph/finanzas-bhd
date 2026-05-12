package com.pfa.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pfa.core.SessionHandle;
import com.pfa.core.SessionSnapshot;
import com.pfa.persistence.DefaultSessionManager;

/**
 * Tests for session save/load logic as wired in AppController.wireSettingsView().
 * Validates: Requirements 10.1, 10.2, 10.4, 10.5
 *
 * The AppController:
 * - Prompts for a session name (max 100 chars)
 * - Checks if the name exists in facade.listSessions() for overwrite confirmation
 * - Calls facade.saveSession(name) and refreshes the sessions list
 * - On load, selects from the list, calls facade.loadSession(handle), refreshes all views
 */
class SessionSaveLoadTest {

    @TempDir
    Path tempDir;

    private DefaultSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new DefaultSessionManager(tempDir);
    }

    @Test
    void saveSessionCreatesNewEntry() {
        SessionSnapshot snapshot = createEmptySnapshot();

        SessionHandle handle = sessionManager.save("My Session", snapshot);

        assertNotNull(handle);
        assertEquals("My Session", handle.name());
        assertTrue(Files.exists(handle.file()));
    }

    @Test
    void listSessionsReturnsAllSaved() {
        sessionManager.save("Session A", createEmptySnapshot());
        sessionManager.save("Session B", createEmptySnapshot());

        List<SessionHandle> sessions = sessionManager.list();

        assertEquals(2, sessions.size());
    }

    @Test
    void saveWithExistingNameOverwritesFile() {
        SessionSnapshot first = createEmptySnapshot();
        sessionManager.save("Overwrite Me", first);

        // Save again with same name — simulates what happens after user confirms overwrite
        SessionSnapshot second = createEmptySnapshot();
        sessionManager.save("Overwrite Me", second);

        // Should still be only one session with that name
        List<SessionHandle> sessions = sessionManager.list();
        long count = sessions.stream()
                .filter(h -> h.name().contains("Overwrite_Me") || h.name().contains("Overwrite Me"))
                .count();
        assertEquals(1, count);
    }

    @Test
    void existingSessionCanBeDetectedByName() {
        sessionManager.save("Budget 2026", createEmptySnapshot());

        // This is what AppController does to check for overwrite
        List<SessionHandle> sessions = sessionManager.list();
        boolean exists = sessions.stream()
                .anyMatch(h -> h.name().equals("Budget_2026") || h.name().contains("Budget"));

        assertTrue(exists, "Should detect existing session for overwrite confirmation");
    }

    @Test
    void loadSessionRestoresData() {
        SessionSnapshot original = createEmptySnapshot();
        SessionHandle handle = sessionManager.save("Loadable", original);

        SessionSnapshot loaded = sessionManager.load(handle);

        assertNotNull(loaded);
        assertEquals("1.0.0", loaded.schemaVersion());
    }

    @Test
    void emptySessionsListWhenNoneSaved() {
        List<SessionHandle> sessions = sessionManager.list();
        assertTrue(sessions.isEmpty());
    }

    @Test
    void sessionNameValidationRejectsBlank() {
        try {
            sessionManager.save("", createEmptySnapshot());
            assertFalse(true, "Should have thrown for blank name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("blank"));
        }
    }

    @Test
    void sessionNameValidationRejectsOver100Chars() {
        String longName = "A".repeat(101);
        try {
            sessionManager.save(longName, createEmptySnapshot());
            assertFalse(true, "Should have thrown for name > 100 chars");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("100"));
        }
    }

    @Test
    void sessionNameAt100CharsIsAccepted() {
        String maxName = "B".repeat(100);
        SessionHandle handle = sessionManager.save(maxName, createEmptySnapshot());
        assertNotNull(handle);
    }

    private SessionSnapshot createEmptySnapshot() {
        return new SessionSnapshot(
                "1.0.0",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
    }
}
