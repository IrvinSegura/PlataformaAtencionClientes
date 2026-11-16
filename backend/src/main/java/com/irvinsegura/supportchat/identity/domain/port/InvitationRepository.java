package com.irvinsegura.supportchat.identity.domain.port;

import com.irvinsegura.supportchat.identity.domain.model.Email;
import com.irvinsegura.supportchat.identity.domain.model.Invitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {

    void insert(Invitation invitation);

    /**
     * Saves acceptance or revocation.
     */
    void update(Invitation invitation);

    /**
     * Locks the row until the transaction ends, so one invitation cannot be accepted twice at the same time.
     */
    Optional<Invitation> findByTokenHashForUpdate(byte[] tokenHash);

    Optional<Invitation> findById(UUID workspaceId, UUID id);

    /**
     * Invitations of the workspace that can still be accepted, newest first.
     */
    List<Invitation> pending(UUID workspaceId, Instant now);

    /**
     * Revokes the pending invitations for this email, so only the newest link works.
     */
    void revokePending(UUID workspaceId, Email email, Instant now);
}
