package com.irvinsegura.supportchat.shared.domain;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates UUIDv7 identifiers (RFC 9562). They are time-ordered, so new rows land at the end of B-tree indexes
 * instead of scattering across them, and their 74 random bits keep them unguessable.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static UUID newId() {
        long millis = System.currentTimeMillis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        // 48 bits of Unix milliseconds, the version (7), then 12 random bits
        long mostSignificant = (millis << 16) | 0x7000L | ((random[0] & 0x0FL) << 8) | (random[1] & 0xFFL);
        long leastSignificant = 0;
        for (int i = 2; i < random.length; i++) {
            leastSignificant = (leastSignificant << 8) | (random[i] & 0xFFL);
        }
        // The variant (binary 10), then 62 random bits
        leastSignificant = (leastSignificant & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
