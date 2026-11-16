package com.irvinsegura.supportchat.identity.infrastructure.persistence;

import com.irvinsegura.supportchat.identity.domain.model.Email;
import com.irvinsegura.supportchat.identity.domain.model.Invitation;
import com.irvinsegura.supportchat.identity.domain.port.InvitationRepository;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInvitationRepository implements InvitationRepository {

    private static final String COLUMNS = """
            id, workspace_id, email, role, token_hash, invited_by, created_at, expires_at, accepted_at, revoked_at""";

    private final JdbcClient jdbc;

    JdbcInvitationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Invitation invitation) {
        jdbc.sql("""
                        INSERT INTO invitations (id, workspace_id, email, role, token_hash, invited_by, created_at,
                                                 expires_at)
                        VALUES (:id, :workspaceId, :email, :role, :tokenHash, :invitedBy, :createdAt, :expiresAt)
                        """)
                .param("id", invitation.id())
                .param("workspaceId", invitation.workspaceId())
                .param("email", invitation.email().value())
                .param("role", invitation.role().name())
                .param("tokenHash", invitation.tokenHash())
                .param("invitedBy", invitation.invitedBy())
                .param("createdAt", Timestamp.from(invitation.createdAt()))
                .param("expiresAt", Timestamp.from(invitation.expiresAt()))
                .update();
    }

    @Override
    public void update(Invitation invitation) {
        jdbc.sql("UPDATE invitations SET accepted_at = :acceptedAt, revoked_at = :revokedAt WHERE id = :id")
                .param("acceptedAt", timestamp(invitation.acceptedAt()))
                .param("revokedAt", timestamp(invitation.revokedAt()))
                .param("id", invitation.id())
                .update();
    }

    @Override
    public Optional<Invitation> findByTokenHashForUpdate(byte[] tokenHash) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM invitations WHERE token_hash = :hash FOR UPDATE")
                .param("hash", tokenHash)
                .query(JdbcInvitationRepository::map)
                .optional();
    }

    @Override
    public Optional<Invitation> findById(UUID workspaceId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM invitations WHERE id = :id AND workspace_id = :workspaceId")
                .param("id", id)
                .param("workspaceId", workspaceId)
                .query(JdbcInvitationRepository::map)
                .optional();
    }

    @Override
    public List<Invitation> pending(UUID workspaceId, Instant now) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM invitations
                        WHERE workspace_id = :workspaceId AND accepted_at IS NULL AND revoked_at IS NULL
                          AND expires_at > :now
                        ORDER BY created_at DESC, id DESC
                        """)
                .param("workspaceId", workspaceId)
                .param("now", Timestamp.from(now))
                .query(JdbcInvitationRepository::map)
                .list();
    }

    @Override
    public void revokePending(UUID workspaceId, Email email, Instant now) {
        jdbc.sql("""
                        UPDATE invitations SET revoked_at = :now
                        WHERE workspace_id = :workspaceId AND email = :email
                          AND accepted_at IS NULL AND revoked_at IS NULL
                        """)
                .param("now", Timestamp.from(now))
                .param("workspaceId", workspaceId)
                .param("email", email.value())
                .update();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Invitation map(ResultSet rs, int row) throws SQLException {
        return Invitation.restore(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                new Email(rs.getString("email")),
                WorkspaceRole.valueOf(rs.getString("role")),
                rs.getBytes("token_hash"),
                rs.getObject("invited_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                instant(rs, "accepted_at"),
                instant(rs, "revoked_at"));
    }
}
