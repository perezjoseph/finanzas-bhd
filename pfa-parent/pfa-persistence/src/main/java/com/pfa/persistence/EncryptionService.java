package com.pfa.persistence;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Provides AES-256-GCM encryption and decryption for session data.
 * <p>
 * File format: [12-byte IV][ciphertext + 16-byte GCM tag]
 */
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32; // AES-256

    private final SecureRandom secureRandom;

    public EncryptionService() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts plaintext data using AES-256-GCM with the provided key.
     *
     * @param plaintext the data to encrypt
     * @param key       the 256-bit encryption key
     * @return encrypted bytes: [12-byte IV][ciphertext + GCM tag]
     * @throws EncryptionException if encryption fails
     */
    public byte[] encrypt(byte[] plaintext, byte[] key) throws EncryptionException {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        validateKey(key);

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            SecretKey secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext);

            // Prepend IV to ciphertext
            byte[] result = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH_BYTES, ciphertext.length);

            return result;
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts data that was encrypted with {@link #encrypt(byte[], byte[])}.
     *
     * @param encrypted the encrypted data: [12-byte IV][ciphertext + GCM tag]
     * @param key       the 256-bit encryption key
     * @return the decrypted plaintext
     * @throws EncryptionException if decryption fails (wrong key, tampered data, etc.)
     */
    public byte[] decrypt(byte[] encrypted, byte[] key) throws EncryptionException {
        Objects.requireNonNull(encrypted, "encrypted data must not be null");
        validateKey(key);

        if (encrypted.length < IV_LENGTH_BYTES + TAG_LENGTH_BITS / 8) {
            throw new EncryptionException("Encrypted data too short to contain IV and tag");
        }

        try {
            byte[] iv = Arrays.copyOfRange(encrypted, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, IV_LENGTH_BYTES, encrypted.length);

            SecretKey secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a new random 256-bit key suitable for AES-256-GCM.
     */
    public byte[] generateKey() {
        byte[] key = new byte[KEY_LENGTH_BYTES];
        secureRandom.nextBytes(key);
        return key;
    }

    private void validateKey(byte[] key) throws EncryptionException {
        if (key == null) {
            throw new EncryptionException("Encryption key must not be null");
        }
        if (key.length != KEY_LENGTH_BYTES) {
            throw new EncryptionException(
                    "Encryption key must be " + KEY_LENGTH_BYTES + " bytes (AES-256), got " + key.length);
        }
    }
}
