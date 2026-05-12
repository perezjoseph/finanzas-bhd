package com.pfa.persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pfa.core.SessionHandle;
import com.pfa.core.SessionSnapshot;

/**
 * Integration tests verifying that DefaultSessionManager encrypts session data
 * on save and decrypts on load, and aborts save on encryption failure while
 * retaining the previous version.
 * <p>
 * Validates: Requirements 12.1, 12.2
 */
class EncryptedSessionSaveLoadTest {

    @TempDir
    Path tempDir;

    private DefaultSessionManager sessionManager;
    private SessionEncryptionManager encryptionManager;
    private VaultManager vaultManager;

    @BeforeEach
    void setUp() {
        EncryptionService encryptionService = new EncryptionService();
        KeyDerivationService keyDerivationService = new KeyDerivationService();
        DpapiKeyStore dpapiKeyStore = new DpapiKeyStore(tempDir);
        encryptionManager = new SessionEncryptionManager(encryptionService, keyDerivationService, dpapiKeyStore);
        vaultManager = new VaultManager();
        sessionManager = new DefaultSessionManager(tempDir, encryptionManager, vaultManager);
    }

    @Test
    void saveEncryptsDataWithDpapi() {
        SessionSnapshot snapshot = createEmptySnapshot();

        SessionHandle handle = sessionManager.save("encrypted-test", snapshot);

        // The file on disk should be encrypted (not plain JSON)
        assertTrue(Files.exists(handle.file()));
        try {
            byte[] rawBytes = Files.readAllBytes(handle.file());
            // Encrypted data starts with version byte (1) and mode byte (0 for DPAPI)
            assertTrue(encryptionManager.isEncrypted(rawBytes),
                    "Saved file should be encrypted");
            assertFalse(encryptionManager.isVaultMode(rawBytes),
                    "Should use DPAPI mode when vault is disabled");
            // Verify it's not plain JSON
            String content = new String(rawBytes, StandardCharsets.UTF_8);
            assertFalse(content.contains("\"schemaVersion\""),
                    "Encrypted file should not contain readable JSON");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void saveAndLoadRoundTripWithDpapi() {
        SessionSnapshot snapshot = createEmptySnapshot();

        SessionHandle handle = sessionManager.save("dpapi-roundtrip", snapshot);
        SessionSnapshot loaded = sessionManager.load(handle);

        assertNotNull(loaded);
        assertEquals("1.0.0", loaded.schemaVersion());
    }

    @Test
    void saveEncryptsDataWithVaultPassword() {
        vaultManager.enableVault("mySecretPassword");
        sessionManager.setActiveVaultPassword("mySecretPassword".toCharArray());

        SessionSnapshot snapshot = createEmptySnapshot();
        SessionHandle handle = sessionManager.save("vault-test", snapshot);

        try {
            byte[] rawBytes = Files.readAllBytes(handle.file());
            assertTrue(encryptionManager.isEncrypted(rawBytes),
                    "Saved file should be encrypted");
            assertTrue(encryptionManager.isVaultMode(rawBytes),
                    "Should use vault mode when vault is enabled");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void saveAndLoadRoundTripWithVaultPassword() {
        vaultManager.enableVault("vaultPass123");
        sessionManager.setActiveVaultPassword("vaultPass123".toCharArray());

        SessionSnapshot snapshot = createEmptySnapshot();
        SessionHandle handle = sessionManager.save("vault-roundtrip", snapshot);
        SessionSnapshot loaded = sessionManager.load(handle);

        assertNotNull(loaded);
        assertEquals("1.0.0", loaded.schemaVersion());
    }

    @Test
    void loadWithWrongVaultPasswordFails() {
        vaultManager.enableVault("correctPassword");
        sessionManager.setActiveVaultPassword("correctPassword".toCharArray());

        SessionSnapshot snapshot = createEmptySnapshot();
        SessionHandle handle = sessionManager.save("wrong-pass-test", snapshot);

        // Change the active password to a wrong one
        sessionManager.setActiveVaultPassword("wrongPassword".toCharArray());

        // Load should fail because decryption will fail with wrong password
        assertThrows(java.io.UncheckedIOException.class, () -> sessionManager.load(handle));
    }

    @Test
    void encryptionFailureRetainsPreviousVersion() {
        // Save a valid session first
        SessionSnapshot firstSnapshot = createEmptySnapshot();
        SessionHandle handle = sessionManager.save("retain-test", firstSnapshot);
        assertTrue(Files.exists(handle.file()));

        // Get the file content after first save
        byte[] firstSaveContent;
        try {
            firstSaveContent = Files.readAllBytes(handle.file());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }

        // Now create a session manager with a broken encryption manager that always fails
        EncryptionService failingEncryption = new EncryptionService() {
            @Override
            public byte[] encrypt(byte[] plaintext, byte[] key) throws EncryptionException {
                throw new EncryptionException("Simulated encryption failure");
            }
        };
        KeyDerivationService kds = new KeyDerivationService();
        DpapiKeyStore dpapiStore = new DpapiKeyStore(tempDir);
        SessionEncryptionManager failingManager = new SessionEncryptionManager(
                failingEncryption, kds, dpapiStore);
        DefaultSessionManager failingSessionManager = new DefaultSessionManager(
                tempDir, failingManager, vaultManager);

        // Attempt to save again — should fail and retain previous version
        SessionSnapshot secondSnapshot = createEmptySnapshot();
        assertThrows(java.io.UncheckedIOException.class,
                () -> failingSessionManager.save("retain-test", secondSnapshot));

        // Verify the original file is still intact
        try {
            byte[] afterFailContent = Files.readAllBytes(handle.file());
            assertArrayEquals(firstSaveContent, afterFailContent,
                    "Previous version should be retained after encryption failure");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
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
