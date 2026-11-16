package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An invitation for one email address to join a workspace with a role. Whoever holds the link's token can accept
 * it, but only into the account of the invited email, and only once, before it expires.
 */
public final class Invitation {

    private final UUID id;
    private final UUID workspaceId;
    private final Email email;
    private final WorkspaceRole role;
    private final byte[] tokenHash;
    private final UUID invitedBy;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant acceptedAt;
    private Instant revokedAt;

    private Invitation(UUID id, UUID workspaceId, Email email, WorkspaceRole role, byte[] tokenHash, UUID invitedBy,
            Instant createdAt, Instant expiresAt, Instant acceptedAt, Instant revokedAt) {
        this.id = Objects.requireNonNull(id);
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.email = Objects.requireNonNull(email);
        this.role = Objects.requireNonNull(role);
        this.tokenHash = tokenHash.clone();
        this.invitedBy = invitedBy;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
    }

    public static Invitation issue(UUID id, UUID workspaceId, Email email, WorkspaceRole role, byte[] tokenHash,
            UUID invitedBy, Duration validity, Instant now) {
        if (role == WorkspaceRole.OWNER) {
            throw new InvalidInvitationRoleException();
        }
        return new Invitation(id, workspaceId, email, role, tokenHash, invitedBy, now, now.plus(validity), null, null);
    }

    public static Invitation restore(UUID id, UUID workspaceId, Email email, WorkspaceRole role, byte[] tokenHash,
            UUID invitedBy, Instant createdAt, Instant expiresAt, Instant acceptedAt, Instant revokedAt) {
        return new Invitation(id, workspaceId, email, role, tokenHash, invitedBy, createdAt, expiresAt, acceptedAt,
                revokedAt);
    }

    public boolean isPendingAt(Instant now) {
        return acceptedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    /**
     * @throws InvalidInvitationException when it was used, revoked or expired; the caller cannot tell which
     */
    public void accept(Instant now) {
        if (!isPendingAt(now)) {
            throw new InvalidInvitationException();
        }
        acceptedAt = now;
    }

    public void revoke(Instant now) {
        if (acceptedAt == null && revokedAt == null) {
            revokedAt = now;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public Email email() {
        return email;
    }

    public WorkspaceRole role() {
        return role;
    }

    public byte[] tokenHash() {
        return tokenHash.clone();
    }

    public UUID invitedBy() {
        return invitedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
