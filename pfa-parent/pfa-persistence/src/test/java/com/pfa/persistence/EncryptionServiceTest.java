package com.pfa.persistence;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EncryptionService (AES-256-GCM).
 */
class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
    }

    @Test
    void encryptAndDecryptRoundTrip() throws EncryptionException {
        byte[] key = encryptionService.generateKey();
        byte[] plaintext = "Hello, encrypted world!".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = encryptionService.encrypt(plaintext, key);
        byte[] decrypted = encryptionService.decrypt(encrypted, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptProducesDifferentCiphertextEachTime() throws EncryptionException {
        byte[] key = encryptionService.generateKey();
        byte[] plaintext = "Same input".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted1 = encryptionService.encrypt(plaintext, key);
        byte[] encrypted2 = encryptionService.encrypt(plaintext, key);

        // Different IVs should produce different ciphertext
        assertFalse(java.util.Arrays.equals(encrypted1, encrypted2));
    }

    @Test
    void decryptWithWrongKeyFails() throws EncryptionException {
        byte[] key1 = encryptionService.generateKey();
        byte[] key2 = encryptionService.generateKey();
        byte[] plaintext = "Secret data".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = encryptionService.encrypt(plaintext, key1);

        assertThrows(EncryptionException.class, () -> encryptionService.decrypt(encrypted, key2));
    }

    @Test
    void decryptTamperedDataFails() throws EncryptionException {
        byte[] key = encryptionService.generateKey();
        byte[] plaintext = "Tamper test".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = encryptionService.encrypt(plaintext, key);
        // Tamper with the ciphertext (not the IV)
        encrypted[encrypted.length - 1] ^= 0xFF;

        assertThrows(EncryptionException.class, () -> encryptionService.decrypt(encrypted, key));
    }

    @Test
    void encryptNullPlaintextThrows() {
        byte[] key = encryptionService.generateKey();
        assertThrows(NullPointerException.class, () -> encryptionService.encrypt(null, key));
    }

    @Test
    void encryptNullKeyThrows() {
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
        assertThrows(EncryptionException.class, () -> encryptionService.encrypt(plaintext, null));
    }

    @Test
    void encryptWrongKeySizeThrows() {
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
        byte[] shortKey = new byte[16]; // AES-128, not AES-256
        assertThrows(EncryptionException.class, () -> encryptionService.encrypt(plaintext, shortKey));
    }

    @Test
    void decryptTooShortDataThrows() {
        byte[] key = encryptionService.generateKey();
        byte[] tooShort = new byte[10]; // Less than IV + tag
        assertThrows(EncryptionException.class, () -> encryptionService.decrypt(tooShort, key));
    }

    @Test
    void generateKeyProduces32Bytes() {
        byte[] key = encryptionService.generateKey();
        assertEquals(32, key.length);
    }

    @Test
    void encryptEmptyPlaintext() throws EncryptionException {
        byte[] key = encryptionService.generateKey();
        byte[] plaintext = new byte[0];

        byte[] encrypted = encryptionService.encrypt(plaintext, key);
        byte[] decrypted = encryptionService.decrypt(encrypted, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptLargeData() throws EncryptionException {
        byte[] key = encryptionService.generateKey();
        byte[] plaintext = new byte[1024 * 1024]; // 1 MB
        new SecureRandom().nextBytes(plaintext);

        byte[] encrypted = encryptionService.encrypt(plaintext, key);
        byte[] decrypted = encryptionService.decrypt(encrypted, key);

        assertArrayEquals(plaintext, decrypted);
    }
}
