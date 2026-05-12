package com.pfa.persistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for SessionEncryptionManager.
 */
class SessionEncryptionManagerTest {

    @TempDir
    Path tempDir;

    private SessionEncryptionManager manager;
    private EncryptionService encryptionService;
    private KeyDerivationService keyDerivationService;
    private DpapiKeyStore dpapiKeyStore;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        keyDerivationService = new KeyDerivationService();
        dpapiKeyStore = new DpapiKeyStore(tempDir);
        manager = new SessionEncryptionManager(encryptionService, keyDerivationService, dpapiKeyStore);
    }

    @Test
    void encryptAndDecryptWithDpapi() throws EncryptionException {
        byte[] plaintext = "Session data for DPAPI test".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = manager.encryptWithDpapi(plaintext);
        byte[] decrypted = manager.decrypt(encrypted, null);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptAndDecryptWithVaultPassword() throws EncryptionException {
        byte[] plaintext = "Session data for vault test".getBytes(StandardCharsets.UTF_8);
        char[] password = "myVaultPassword123".toCharArray();

        byte[] encrypted = manager.encryptWithVaultPassword(plaintext, password);
        byte[] decrypted = manager.decrypt(encrypted, password);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void decryptVaultWithWrongPasswordFails() throws EncryptionException {
        byte[] plaintext = "Secret session".getBytes(StandardCharsets.UTF_8);
        char[] correctPassword = "correct".toCharArray();
        char[] wrongPassword = "wrong".toCharArray();

        byte[] encrypted = manager.encryptWithVaultPassword(plaintext, correctPassword);

        assertThrows(EncryptionException.class, () -> manager.decrypt(encrypted, wrongPassword));
    }

    @Test
    void isEncryptedDetectsEncryptedData() throws EncryptionException {
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = manager.encryptWithDpapi(plaintext);

        assertTrue(manager.isEncrypted(encrypted));
    }

    @Test
    void isEncryptedReturnsFalseForPlainData() {
        byte[] plainJson = "{\"schemaVersion\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8);

        assertFalse(manager.isEncrypted(plainJson));
    }

    @Test
    void isVaultModeDetectsVaultEncryption() throws EncryptionException {
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);

        byte[] dpapiEncrypted = manager.encryptWithDpapi(plaintext);
        byte[] vaultEncrypted = manager.encryptWithVaultPassword(plaintext, "pass".toCharArray());

        assertFalse(manager.isVaultMode(dpapiEncrypted));
        assertTrue(manager.isVaultMode(vaultEncrypted));
    }

    @Test
    void decryptVaultWithNullPasswordFails() throws EncryptionException {
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = manager.encryptWithVaultPassword(plaintext, "pass".toCharArray());

        assertThrows(EncryptionException.class, () -> manager.decrypt(encrypted, null));
    }

    @Test
    void encryptWithNullPlaintextThrows() {
        assertThrows(NullPointerException.class, () -> manager.encryptWithDpapi(null));
    }

    @Test
    void decryptTooShortDataThrows() {
        byte[] tooShort = new byte[]{1};
        assertThrows(EncryptionException.class, () -> manager.decrypt(tooShort, null));
    }
}
