package com.pfa.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for KeyDerivationService (Argon2id).
 */
class KeyDerivationServiceTest {

    private KeyDerivationService keyDerivationService;

    @BeforeEach
    void setUp() {
        keyDerivationService = new KeyDerivationService();
    }

    @Test
    void deriveKeyProduces32Bytes() {
        byte[] salt = keyDerivationService.generateSalt();
        byte[] key = keyDerivationService.deriveKey("myPassword".toCharArray(), salt);

        assertEquals(32, key.length);
    }

    @Test
    void samePasswordAndSaltProduceSameKey() {
        byte[] salt = keyDerivationService.generateSalt();
        char[] password = "consistentPassword".toCharArray();

        byte[] key1 = keyDerivationService.deriveKey(password, salt);
        byte[] key2 = keyDerivationService.deriveKey(password, salt);

        assertArrayEquals(key1, key2);
    }

    @Test
    void differentPasswordsProduceDifferentKeys() {
        byte[] salt = keyDerivationService.generateSalt();

        byte[] key1 = keyDerivationService.deriveKey("password1".toCharArray(), salt);
        byte[] key2 = keyDerivationService.deriveKey("password2".toCharArray(), salt);

        assertFalse(java.util.Arrays.equals(key1, key2));
    }

    @Test
    void differentSaltsProduceDifferentKeys() {
        char[] password = "samePassword".toCharArray();
        byte[] salt1 = keyDerivationService.generateSalt();
        byte[] salt2 = keyDerivationService.generateSalt();

        byte[] key1 = keyDerivationService.deriveKey(password, salt1);
        byte[] key2 = keyDerivationService.deriveKey(password, salt2);

        assertFalse(java.util.Arrays.equals(key1, key2));
    }

    @Test
    void generateSaltProduces16Bytes() {
        byte[] salt = keyDerivationService.generateSalt();
        assertEquals(16, salt.length);
    }

    @Test
    void emptyPasswordThrows() {
        byte[] salt = keyDerivationService.generateSalt();
        assertThrows(IllegalArgumentException.class,
                () -> keyDerivationService.deriveKey(new char[0], salt));
    }

    @Test
    void nullPasswordThrows() {
        byte[] salt = keyDerivationService.generateSalt();
        assertThrows(NullPointerException.class,
                () -> keyDerivationService.deriveKey(null, salt));
    }

    @Test
    void nullSaltThrows() {
        assertThrows(NullPointerException.class,
                () -> keyDerivationService.deriveKey("password".toCharArray(), null));
    }
}
