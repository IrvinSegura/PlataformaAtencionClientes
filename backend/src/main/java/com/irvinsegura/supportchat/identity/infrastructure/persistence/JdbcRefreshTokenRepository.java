package com.irvinsegura.supportchat.identity.infrastructure.persistence;

import com.irvinsegura.supportchat.identity.domain.model.RefreshToken;
import com.irvinsegura.supportchat.identity.domain.port.RefreshTokenRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcClient jdbc;

    JdbcRefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(RefreshToken token, Instant createdAt) {
        jdbc.sql("""
                        INSERT INTO refresh_tokens (id, user_id, workspace_id, family_id, token_hash, expires_at,
                                                    created_at)
                        VALUES (:id, :userId, :workspaceId, :familyId, :tokenHash, :expiresAt, :createdAt)
                        """)
                .param("id", token.id())
                .param("userId", token.userId())
                .param("workspaceId", token.workspaceId())
                .param("familyId", token.familyId())
                .param("tokenHash", token.tokenHash())
                .param("expiresAt", Timestamp.from(token.expiresAt()))
                .param("createdAt", Timestamp.from(createdAt))
                .update();
    }

    @Override
    public Optional<RefreshToken> findByHashForUpdate(byte[] tokenHash) {
        return jdbc.sql("""
                        SELECT id, user_id, workspace_id, family_id, token_hash, expires_at, used_at, revoked_at
                        FROM refresh_tokens
                        WHERE token_hash = :tokenHash
                        FOR UPDATE
                        """)
                .param("tokenHash", tokenHash)
                .query(JdbcRefreshTokenRepository::map)
                .optional();
    }

    @Override
    public void markUsed(UUID id, Instant usedAt) {
        jdbc.sql("UPDATE refresh_tokens SET used_at = :usedAt WHERE id = :id")
                .param("usedAt", Timestamp.from(usedAt))
                .param("id", id)
                .update();
    }

    @Override
    public void revokeFamily(UUID familyId, Instant revokedAt) {
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = :revokedAt WHERE family_id = :familyId AND revoked_at IS NULL")
                .param("revokedAt", Timestamp.from(revokedAt))
                .param("familyId", familyId)
                .update();
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM refresh_tokens WHERE expires_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }

    private static RefreshToken map(ResultSet rs, int row) throws SQLException {
        return new RefreshToken(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getBytes("token_hash"),
                rs.getTimestamp("expires_at").toInstant(),
                instant(rs.getTimestamp("used_at")),
                instant(rs.getTimestamp("revoked_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
