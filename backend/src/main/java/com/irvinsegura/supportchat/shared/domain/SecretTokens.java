package com.irvinsegura.supportchat.shared.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Opaque bearer secrets (refresh tokens, visitor conversation tokens). Only their SHA-256 hash is stored, so a
 * database leak exposes nothing usable. A fast hash is enough here, unlike for passwords: 256 random bits cannot be
 * guessed, so there is nothing to slow down.
 */
public final class SecretTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private SecretTokens() {
    }

    /**
     * A new URL-safe token with 256 bits of entropy.
     */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    /**
     * Compares in constant time, so response timing reveals nothing about how close a guess was.
     */
    public static boolean matches(String token, byte[] expectedHash) {
        return token != null && MessageDigest.isEqual(hash(token), expectedHash);
    }
}
