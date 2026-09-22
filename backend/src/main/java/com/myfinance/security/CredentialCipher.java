package com.myfinance.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Symmetric encryption for broker API credentials at rest, using AES-256-GCM.
 *
 * <p>The master key is supplied out-of-band via {@code app.crypto.master-key} (a base64-encoded
 * 32-byte key sourced from an environment variable / secret store) — it is never stored in the
 * database or the codebase. Each value is encrypted with a fresh random 12-byte IV; the stored
 * ciphertext is {@code base64(IV || ciphertext || GCM-tag)}. GCM provides authenticated
 * encryption, so tampering or a wrong key fails the {@link #decrypt} instead of returning garbage.
 *
 * <p>Fails safe: if no master key is configured, {@link #isEnabled()} is false and callers must not
 * store credentials (we never fall back to plaintext). This class never logs plaintext or the key.
 *
 * <p>KMS-ready: a cloud deployment can source the master key from KMS/Secrets Manager into the env
 * var without any code change; or a KMS-backed implementation can replace this component later.
 */
@Slf4j
@Component
public class CredentialCipher {

    private static final int IV_LENGTH = 12;         // 96-bit IV recommended for GCM
    private static final int TAG_LENGTH_BITS = 128;  // GCM auth tag
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec key;              // null when not configured
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(@Value("${app.crypto.master-key:}") String masterKeyB64) {
        SecretKeySpec k = null;
        if (masterKeyB64 != null && !masterKeyB64.isBlank()) {
            try {
                byte[] raw = Base64.getDecoder().decode(masterKeyB64.trim());
                if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
                    log.error("app.crypto.master-key must be a base64 16/24/32-byte key; broker credential encryption is DISABLED");
                } else {
                    k = new SecretKeySpec(raw, "AES");
                }
            } catch (IllegalArgumentException e) {
                log.error("app.crypto.master-key is not valid base64; broker credential encryption is DISABLED");
            }
        } else {
            log.info("app.crypto.master-key not set — broker credential storage is disabled until a key is configured");
        }
        this.key = k;
    }

    /** True when a valid master key is configured and credentials may be stored/read. */
    public boolean isEnabled() { return key != null; }

    /** Encrypt a plaintext secret → base64(IV||ciphertext||tag). */
    public String encrypt(String plaintext) {
        requireEnabled();
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // Never include the plaintext in the message.
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    /** Decrypt a value produced by {@link #encrypt}. Throws if the key is wrong or data tampered. */
    public String decrypt(String stored) {
        requireEnabled();
        if (stored == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            byte[] ct = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt credential (wrong key or corrupted data)", e);
        }
    }

    private void requireEnabled() {
        if (key == null) {
            throw new IllegalStateException("Credential encryption is not configured (set app.crypto.master-key)");
        }
    }
}
