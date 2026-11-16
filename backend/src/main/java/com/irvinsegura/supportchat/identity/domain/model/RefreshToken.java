package com.irvinsegura.supportchat.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One link in a login's chain of refresh tokens. Each token works once: using it returns a new one in the same
 * family. A token presented again after use means two parties hold it, one of them an attacker, so the whole family
 * is revoked and both must sign in again.
 */
public final class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final UUID workspaceId;
    private final UUID familyId;
    private final byte[] tokenHash;
    private final Instant expiresAt;
    private final Instant usedAt;
    private final Instant revokedAt;

    public RefreshToken(UUID id, UUID userId, UUID workspaceId, UUID familyId, byte[] tokenHash, Instant expiresAt,
            Instant usedAt, Instant revokedAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.familyId = Objects.requireNonNull(familyId);
        this.tokenHash = tokenHash.clone();
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
    }

    public RefreshTokenState stateAt(Instant now) {
        if (revokedAt != null || usedAt != null) {
            return RefreshTokenState.REUSED_OR_REVOKED;
        }
        if (!now.isBefore(expiresAt)) {
            return RefreshTokenState.EXPIRED;
        }
        return RefreshTokenState.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public UUID familyId() {
        return familyId;
    }

    public byte[] tokenHash() {
        return tokenHash.clone();
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public enum RefreshTokenState {
        ACTIVE,
        EXPIRED,
        REUSED_OR_REVOKED
    }
}
