package com.myfinance.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CredentialCipher}: AES-256-GCM round-trip, that a wrong key fails to decrypt
 * (authenticated encryption), and that the cipher is disabled (and refuses to encrypt) with no key.
 */
class CredentialCipherTest {

    private static String key256() {
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) (i + 1);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static String otherKey256() {
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) raw[i] = (byte) (100 - i);
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    void roundTripsAValue() {
        CredentialCipher cipher = new CredentialCipher(key256());
        assertTrue(cipher.isEnabled());

        String secret = "flex-token-abc123!@#";
        String enc = cipher.encrypt(secret);
        assertNotNull(enc);
        assertNotEquals(secret, enc, "ciphertext must not equal plaintext");
        assertEquals(secret, cipher.decrypt(enc), "decrypt reverses encrypt");
    }

    @Test
    void usesAFreshIvSoSamePlaintextEncryptsDifferently() {
        CredentialCipher cipher = new CredentialCipher(key256());
        String a = cipher.encrypt("same");
        String b = cipher.encrypt("same");
        assertNotEquals(a, b, "a random IV per call makes identical plaintext encrypt to different ciphertext");
        assertEquals("same", cipher.decrypt(a));
        assertEquals("same", cipher.decrypt(b));
    }

    @Test
    void wrongKeyCannotDecrypt() {
        CredentialCipher writer = new CredentialCipher(key256());
        String enc = writer.encrypt("private-key-material");

        CredentialCipher attacker = new CredentialCipher(otherKey256());
        assertThrows(IllegalStateException.class, () -> attacker.decrypt(enc),
                "GCM authentication fails under a different key — never returns garbage plaintext");
    }

    @Test
    void disabledWithoutAKey() {
        CredentialCipher cipher = new CredentialCipher("");
        assertFalse(cipher.isEnabled());
        assertThrows(IllegalStateException.class, () -> cipher.encrypt("x"),
                "with no master key configured, encryption must refuse rather than store plaintext");
    }

    @Test
    void invalidBase64KeyDisablesTheCipher() {
        CredentialCipher cipher = new CredentialCipher("not-valid-base64!!!");
        assertFalse(cipher.isEnabled());
    }
}
