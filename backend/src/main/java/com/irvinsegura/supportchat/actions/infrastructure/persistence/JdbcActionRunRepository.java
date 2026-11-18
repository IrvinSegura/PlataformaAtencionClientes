package com.irvinsegura.supportchat.actions.infrastructure.persistence;

import com.irvinsegura.supportchat.actions.ActionRunStatus;
import com.irvinsegura.supportchat.actions.domain.model.ActionRun;
import com.irvinsegura.supportchat.actions.domain.port.ActionRunRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcActionRunRepository implements ActionRunRepository {

    private static final String COLUMNS = """
            id, action_id, action_name, action_title, conversation_id, message_id, arguments, status, http_status,
            result, error, latency_ms, created_at, expires_at, finished_at""";

    private static final TypeReference<LinkedHashMap<String, Object>> ARGUMENTS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcActionRunRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void insert(ActionRun run) {
        // The workspace bound to the current code; the conversation's foreign key keeps the reference valid
        jdbc.sql("""
                        INSERT INTO action_runs (id, workspace_id, action_id, action_name, action_title,
                                                 conversation_id, message_id, arguments, status, http_status, result,
                                                 error, latency_ms, created_at, expires_at, finished_at)
                        VALUES (:id, current_workspace_id(), :actionId, :actionName, :actionTitle, :conversationId,
                                :messageId, CAST(:arguments AS jsonb), :status, :httpStatus, :result, :error,
                                :latencyMs, :createdAt, :expiresAt, :finishedAt)
                        """)
                .param("id", run.id())
                .param("actionId", run.actionId())
                .param("actionName", run.actionName())
                .param("actionTitle", run.actionTitle())
                .param("conversationId", run.conversationId())
                .param("messageId", run.messageId())
                .param("arguments", json.writeValueAsString(run.arguments()))
                .param("status", run.status().name())
                .param("httpStatus", run.httpStatus())
                .param("result", run.result())
                .param("error", run.error())
                .param("latencyMs", run.latencyMs())
                .param("createdAt", Timestamp.from(run.createdAt()))
                .param("expiresAt", timestamp(run.expiresAt()))
                .param("finishedAt", timestamp(run.finishedAt()))
                .update();
    }

    @Override
    @Transactional
    public void update(ActionRun run) {
        jdbc.sql("""
                        UPDATE action_runs
                        SET status = :status, http_status = :httpStatus, result = :result, error = :error,
                            latency_ms = :latencyMs, finished_at = :finishedAt
                        WHERE id = :id
                        """)
                .param("status", run.status().name())
                .param("httpStatus", run.httpStatus())
                .param("result", run.result())
                .param("error", run.error())
                .param("latencyMs", run.latencyMs())
                .param("finishedAt", timestamp(run.finishedAt()))
                .param("id", run.id())
                .update();
    }

    @Override
    @Transactional
    public Optional<ActionRun> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM action_runs WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionRun> byConversation(UUID conversationId) {
        return jdbc.sql("SELECT " + COLUMNS
                        + " FROM action_runs WHERE conversation_id = :conversationId ORDER BY created_at, id")
                .param("conversationId", conversationId)
                .query(this::map)
                .list();
    }

    private ActionRun map(ResultSet rs, int row) throws SQLException {
        Map<String, Object> arguments = json.readValue(rs.getString("arguments"), ARGUMENTS);
        return ActionRun.restore(
                rs.getObject("id", UUID.class),
                rs.getObject("action_id", UUID.class),
                rs.getString("action_name"),
                rs.getString("action_title"),
                rs.getObject("conversation_id", UUID.class),
                rs.getObject("message_id", UUID.class),
                arguments,
                ActionRunStatus.valueOf(rs.getString("status")),
                rs.getObject("http_status", Integer.class),
                rs.getString("result"),
                rs.getString("error"),
                rs.getObject("latency_ms", Integer.class),
                rs.getTimestamp("created_at").toInstant(),
                instant(rs.getTimestamp("expires_at")),
                instant(rs.getTimestamp("finished_at")));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
