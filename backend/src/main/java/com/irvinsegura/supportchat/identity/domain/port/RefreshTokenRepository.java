package com.irvinsegura.supportchat.identity.domain.port;

import com.irvinsegura.supportchat.identity.domain.model.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void insert(RefreshToken token, Instant createdAt);

    /**
     * Locks the row until the transaction ends, so two requests can never both use the same token.
     */
    Optional<RefreshToken> findByHashForUpdate(byte[] tokenHash);

    void markUsed(UUID id, Instant usedAt);

    void revokeFamily(UUID familyId, Instant revokedAt);

    int deleteExpiredBefore(Instant cutoff);
}
