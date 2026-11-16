package com.irvinsegura.supportchat.workspaces.infrastructure.persistence;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.irvinsegura.supportchat.workspaces.domain.model.AllowedOrigin;
import com.irvinsegura.supportchat.workspaces.domain.model.WidgetKey;
import com.irvinsegura.supportchat.workspaces.domain.model.Workspace;
import com.irvinsegura.supportchat.workspaces.domain.port.WorkspaceRepository;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWorkspaceRepository implements WorkspaceRepository {

    private static final String COLUMNS =
            "id, name, widget_key, allowed_origins, bot_name, welcome_message, created_at, updated_at";

    // Every widget request resolves its key, so lookups are cached briefly. Changes evict the entry on this instance;
    // other instances see them within the expiry, which bounds how long a rotated key keeps working
    private static final Duration WIDGET_KEY_CACHE_TTL = Duration.ofSeconds(30);

    private final JdbcClient jdbc;
    private final Cache<String, Optional<Workspace>> byWidgetKey = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(WIDGET_KEY_CACHE_TTL)
            .build();

    JdbcWorkspaceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Workspace workspace) {
        jdbc.sql("""
                        INSERT INTO workspaces (id, name, widget_key, allowed_origins, bot_name, welcome_message,
                                                created_at, updated_at)
                        VALUES (:id, :name, :widgetKey, :allowedOrigins, :botName, :welcomeMessage, :createdAt,
                                :updatedAt)
                        """)
                .params(parameters(workspace))
                .update();
    }

    @Override
    public void update(Workspace workspace) {
        jdbc.sql("""
                        UPDATE workspaces
                        SET widget_key = :widgetKey, allowed_origins = :allowedOrigins, bot_name = :botName,
                            welcome_message = :welcomeMessage, updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .params(parameters(workspace))
                .update();
    }

    @Override
    public Optional<Workspace> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM workspaces WHERE id = :id")
                .param("id", id)
                .query(JdbcWorkspaceRepository::map)
                .optional();
    }

    @Override
    public Optional<Workspace> findByWidgetKey(WidgetKey widgetKey) {
        // Misses are cached too, so a flood of requests with a made-up key never reaches the database
        return byWidgetKey.get(widgetKey.value(), key -> jdbc
                .sql("SELECT " + COLUMNS + " FROM workspaces WHERE widget_key = :widgetKey")
                .param("widgetKey", key)
                .query(JdbcWorkspaceRepository::map)
                .optional());
    }

    @Override
    public void evict(WidgetKey widgetKey) {
        byWidgetKey.invalidate(widgetKey.value());
    }

    private static Map<String, Object> parameters(Workspace workspace) {
        return Map.of(
                "id", workspace.id(),
                "name", workspace.name(),
                "widgetKey", workspace.widgetKey().value(),
                // A String[] is sent as a PostgreSQL text[]
                "allowedOrigins", workspace.allowedOrigins().stream().map(AllowedOrigin::value).toArray(String[]::new),
                "botName", workspace.botName(),
                "welcomeMessage", workspace.welcomeMessage(),
                "createdAt", Timestamp.from(workspace.createdAt()),
                "updatedAt", Timestamp.from(workspace.updatedAt()));
    }

    private static Workspace map(ResultSet rs, int row) throws SQLException {
        return Workspace.restore(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                new WidgetKey(rs.getString("widget_key")),
                origins(rs.getArray("allowed_origins")),
                rs.getString("bot_name"),
                rs.getString("welcome_message"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static List<AllowedOrigin> origins(Array array) throws SQLException {
        // Stored values were normalized when saved, so they are wrapped as they are
        return Arrays.stream((String[]) array.getArray()).map(AllowedOrigin::new).toList();
    }
}
