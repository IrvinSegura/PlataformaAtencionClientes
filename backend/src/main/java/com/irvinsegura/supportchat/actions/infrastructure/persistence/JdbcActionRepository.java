package com.irvinsegura.supportchat.actions.infrastructure.persistence;

import com.irvinsegura.supportchat.actions.ActionParameter;
import com.irvinsegura.supportchat.actions.domain.model.Action;
import com.irvinsegura.supportchat.actions.domain.model.DuplicateActionNameException;
import com.irvinsegura.supportchat.actions.domain.port.ActionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcActionRepository implements ActionRepository {

    private static final String COLUMNS = """
            id, workspace_id, name, title, description, endpoint_url, parameters, requires_confirmation, enabled,
            created_at, updated_at""";

    private static final TypeReference<List<ActionParameter>> PARAMETERS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcActionRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void insert(Action action) {
        write("""
                INSERT INTO assistant_actions (id, workspace_id, name, title, description, endpoint_url, parameters,
                                               requires_confirmation, enabled, created_at, updated_at)
                VALUES (:id, :workspaceId, :name, :title, :description, :endpointUrl, CAST(:parameters AS jsonb),
                        :requiresConfirmation, :enabled, :createdAt, :updatedAt)
                """, action);
    }

    @Override
    @Transactional
    public void update(Action action) {
        write("""
                UPDATE assistant_actions
                SET name = :name, title = :title, description = :description, endpoint_url = :endpointUrl,
                    parameters = CAST(:parameters AS jsonb), requires_confirmation = :requiresConfirmation,
                    enabled = :enabled, updated_at = :updatedAt
                WHERE id = :id
                """, action);
    }

    private void write(String sql, Action action) {
        try {
            jdbc.sql(sql)
                    .param("id", action.id())
                    .param("workspaceId", action.workspaceId())
                    .param("name", action.name())
                    .param("title", action.title())
                    .param("description", action.description())
                    .param("endpointUrl", action.endpointUrl())
                    .param("parameters", json.writeValueAsString(action.parameters()))
                    .param("requiresConfirmation", action.requiresConfirmation())
                    .param("enabled", action.enabled())
                    .param("createdAt", Timestamp.from(action.createdAt()))
                    .param("updatedAt", Timestamp.from(action.updatedAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateActionNameException();
        }
    }

    @Override
    @Transactional
    public boolean delete(UUID id) {
        return jdbc.sql("DELETE FROM assistant_actions WHERE id = :id").param("id", id).update() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Action> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM assistant_actions WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Action> all() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM assistant_actions ORDER BY name")
                .query(this::map)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jdbc.sql("SELECT count(*) FROM assistant_actions").query(Long.class).single();
    }

    private Action map(ResultSet rs, int row) throws SQLException {
        return Action.restore(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("name"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("endpoint_url"),
                json.readValue(rs.getString("parameters"), PARAMETERS),
                rs.getBoolean("requires_confirmation"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
