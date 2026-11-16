package com.irvinsegura.supportchat.identity.domain.model;

import java.time.Duration;

/**
 * After {@code maxFailedAttempts} wrong passwords in a row an account refuses sign-ins for {@code duration}, which
 * makes guessing one account's password impractical. Guessing across accounts is slowed by per-address rate limits.
 */
public record LockoutPolicy(int maxFailedAttempts, Duration duration) {

    public LockoutPolicy {
        if (maxFailedAttempts < 1 || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Lockout needs at least one attempt and a positive duration");
        }
    }
}
