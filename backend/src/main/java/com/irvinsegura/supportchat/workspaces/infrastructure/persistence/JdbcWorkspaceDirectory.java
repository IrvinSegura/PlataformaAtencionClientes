package com.irvinsegura.supportchat.workspaces.infrastructure.persistence;

import com.irvinsegura.supportchat.workspaces.WorkspaceSummary;
import com.irvinsegura.supportchat.workspaces.WorkspaceSuspension;
import com.irvinsegura.supportchat.workspaces.domain.port.WorkspaceDirectory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads across workspaces on purpose: the workspaces, their members and suspensions have no row-level security, and
 * nothing here reaches a workspace's own data.
 */
@Repository
class JdbcWorkspaceDirectory implements WorkspaceDirectory {

    private static final String SUMMARIES = """
            SELECT w.id, w.name, w.created_at,
                   (SELECT count(*) FROM workspace_members m WHERE m.workspace_id = w.id) AS members,
                   (SELECT m.user_id FROM workspace_members m
                     WHERE m.workspace_id = w.id AND m.role = 'OWNER' LIMIT 1) AS owner_user_id,
                   s.reason, s.suspended_at
            FROM workspaces w
            LEFT JOIN workspace_suspensions s ON s.workspace_id = w.id
            """;

    private final JdbcClient jdbc;

    JdbcWorkspaceDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Page page(String query, int page, int size) {
        String where = query == null ? "" : " WHERE w.name ILIKE :pattern ESCAPE '\\'";
        var rows = jdbc.sql(SUMMARIES + where + " ORDER BY w.created_at DESC, w.id LIMIT :limit OFFSET :offset")
                .param("limit", size)
                .param("offset", (long) page * size);
        var count = jdbc.sql("SELECT count(*) FROM workspaces w" + where);
        if (query != null) {
            String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            rows = rows.param("pattern", pattern);
            count = count.param("pattern", pattern);
        }
        return new Page(rows.query(JdbcWorkspaceDirectory::summary).list(), count.query(Long.class).single());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceSummary> find(UUID workspaceId) {
        return jdbc.sql(SUMMARIES + " WHERE w.id = :id")
                .param("id", workspaceId)
                .query(JdbcWorkspaceDirectory::summary)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceSuspension> suspension(UUID workspaceId) {
        return jdbc.sql("SELECT reason, suspended_at FROM workspace_suspensions WHERE workspace_id = :id")
                .param("id", workspaceId)
                .query((rs, row) -> new WorkspaceSuspension(rs.getString("reason"),
                        rs.getTimestamp("suspended_at").toInstant()))
                .optional();
    }

    @Override
    @Transactional
    public void suspend(UUID workspaceId, String reason, UUID suspendedBy, Instant now) {
        jdbc.sql("""
                        INSERT INTO workspace_suspensions (workspace_id, reason, suspended_at, suspended_by)
                        VALUES (:id, :reason, :now, :by)
                        ON CONFLICT (workspace_id) DO UPDATE SET reason = EXCLUDED.reason
                        """)
                .param("id", workspaceId)
                .param("reason", reason)
                .param("now", Timestamp.from(now))
                .param("by", suspendedBy)
                .update();
    }

    @Override
    @Transactional
    public boolean reinstate(UUID workspaceId) {
        return jdbc.sql("DELETE FROM workspace_suspensions WHERE workspace_id = :id")
                .param("id", workspaceId)
                .update() == 1;
    }

    private static WorkspaceSummary summary(ResultSet rs, int row) throws SQLException {
        Timestamp suspendedAt = rs.getTimestamp("suspended_at");
        return new WorkspaceSummary(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getTimestamp("created_at").toInstant(), rs.getInt("members"),
                rs.getObject("owner_user_id", UUID.class),
                suspendedAt == null ? null : new WorkspaceSuspension(rs.getString("reason"), suspendedAt.toInstant()));
    }
}
