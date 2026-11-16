package com.irvinsegura.supportchat.workspaces.infrastructure.persistence;

import com.irvinsegura.supportchat.workspaces.Member;
import com.irvinsegura.supportchat.workspaces.Membership;
import com.irvinsegura.supportchat.workspaces.WorkspaceMembership;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import com.irvinsegura.supportchat.workspaces.domain.port.MemberRepository;
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
class JdbcMemberRepository implements MemberRepository {

    private final JdbcClient jdbc;

    JdbcMemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(UUID workspaceId, UUID userId, WorkspaceRole role, Instant now) {
        jdbc.sql("""
                        INSERT INTO workspace_members (workspace_id, user_id, role, created_at)
                        VALUES (:workspaceId, :userId, :role, :createdAt)
                        """)
                .param("workspaceId", workspaceId)
                .param("userId", userId)
                .param("role", role.name())
                .param("createdAt", Timestamp.from(now))
                .update();
    }

    @Override
    public Optional<Membership> findOldestOf(UUID userId) {
        return jdbc.sql("""
                        SELECT workspace_id, role FROM workspace_members
                        WHERE user_id = :userId
                        ORDER BY created_at, workspace_id
                        LIMIT 1
                        """)
                .param("userId", userId)
                .query(JdbcMemberRepository::map)
                .optional();
    }

    @Override
    public Optional<Membership> find(UUID userId, UUID workspaceId) {
        return jdbc.sql("""
                        SELECT workspace_id, role FROM workspace_members
                        WHERE user_id = :userId AND workspace_id = :workspaceId
                        """)
                .param("userId", userId)
                .param("workspaceId", workspaceId)
                .query(JdbcMemberRepository::map)
                .optional();
    }

    @Override
    public List<Member> list(UUID workspaceId) {
        return jdbc.sql("""
                        SELECT user_id, role, created_at FROM workspace_members
                        WHERE workspace_id = :workspaceId
                        ORDER BY created_at, user_id
                        """)
                .param("workspaceId", workspaceId)
                .query((rs, row) -> new Member(rs.getObject("user_id", UUID.class),
                        WorkspaceRole.valueOf(rs.getString("role")), rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public List<WorkspaceMembership> membershipsOf(UUID userId) {
        return jdbc.sql("""
                        SELECT m.workspace_id, w.name, m.role
                        FROM workspace_members m JOIN workspaces w ON w.id = m.workspace_id
                        WHERE m.user_id = :userId
                        ORDER BY m.created_at, m.workspace_id
                        """)
                .param("userId", userId)
                .query((rs, row) -> new WorkspaceMembership(rs.getObject("workspace_id", UUID.class),
                        rs.getString("name"), WorkspaceRole.valueOf(rs.getString("role"))))
                .list();
    }

    @Override
    public void changeRole(UUID workspaceId, UUID userId, WorkspaceRole role) {
        jdbc.sql("UPDATE workspace_members SET role = :role WHERE workspace_id = :workspaceId AND user_id = :userId")
                .param("role", role.name())
                .param("workspaceId", workspaceId)
                .param("userId", userId)
                .update();
    }

    @Override
    public void remove(UUID workspaceId, UUID userId) {
        jdbc.sql("DELETE FROM workspace_members WHERE workspace_id = :workspaceId AND user_id = :userId")
                .param("workspaceId", workspaceId)
                .param("userId", userId)
                .update();
    }

    private static Membership map(ResultSet rs, int row) throws SQLException {
        return new Membership(rs.getObject("workspace_id", UUID.class), WorkspaceRole.valueOf(rs.getString("role")));
    }
}
