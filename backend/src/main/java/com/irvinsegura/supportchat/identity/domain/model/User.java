package com.irvinsegura.supportchat.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An operator account.
 */
public final class User {

    public static final int MAX_DISPLAY_NAME_LENGTH = 80;

    private final UUID id;
    private final Email email;
    private final String displayName;
    private final String passwordHash;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private final Instant createdAt;

    private User(UUID id, Email email, String displayName, String passwordHash, int failedLoginAttempts,
            Instant lockedUntil, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.displayName = displayName;
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static User register(UUID id, Email email, String displayName, String passwordHash, Instant now) {
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty() || name.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new InvalidProfileException(
                    "displayName must have between 1 and %d characters".formatted(MAX_DISPLAY_NAME_LENGTH));
        }
        return new User(id, email, name, passwordHash, 0, null, now);
    }

    public static User restore(UUID id, Email email, String displayName, String passwordHash,
            int failedLoginAttempts, Instant lockedUntil, Instant createdAt) {
        return new User(id, email, displayName, passwordHash, failedLoginAttempts, lockedUntil, createdAt);
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Counts a wrong password; the last allowed one locks the account and starts the count again.
     *
     * @return whether this attempt locked the account
     */
    public boolean recordFailedLogin(LockoutPolicy policy, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= policy.maxFailedAttempts()) {
            failedLoginAttempts = 0;
            lockedUntil = now.plus(policy.duration());
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public UUID id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
