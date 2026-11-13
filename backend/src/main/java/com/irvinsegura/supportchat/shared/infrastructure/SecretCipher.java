package com.irvinsegura.supportchat.shared.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts secrets the platform must be able to read back, unlike tokens it only compares (those are hashed): the key
 * that signs a workspace's action webhooks, or the token that sends its WhatsApp messages. AES-256-GCM with a random
 * nonce per value, so a database dump without the key reveals nothing and a tampered value fails to decrypt.
 * <p>
 * Values are stored as {@code v1:<base64(nonce + ciphertext + tag)>}; the prefix leaves room to rotate the key later.
 */
public final class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public SecretCipher(byte[] key) {
        if (key.length != 32) {
            throw new IllegalArgumentException("The secrets encryption key must be 32 bytes (256 bits)");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
    }

    public String encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] stored = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a secret", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Not an encrypted secret");
        }
        byte[] bytes = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES));
            byte[] plain = cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Wrong key or altered value: never fall back to returning the stored text
            throw new IllegalStateException("Could not decrypt a secret; is SECRETS_ENCRYPTION_KEY the one used to "
                    + "store it?", e);
        }
    }
}
