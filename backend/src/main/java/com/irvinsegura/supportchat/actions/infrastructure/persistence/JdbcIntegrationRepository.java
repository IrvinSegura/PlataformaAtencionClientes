package com.irvinsegura.supportchat.actions.infrastructure.persistence;

import com.irvinsegura.supportchat.actions.domain.model.Integration;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationProvider;
import com.irvinsegura.supportchat.actions.domain.port.IntegrationRepository;
import com.irvinsegura.supportchat.shared.infrastructure.SecretCipher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcIntegrationRepository implements IntegrationRepository {

    private static final String SELECT = """
            SELECT workspace_id, provider, account, username, token, enabled, created_at, updated_at
            FROM action_integrations
            """;

    private final JdbcClient jdbc;
    private final SecretCipher cipher;

    JdbcIntegrationRepository(JdbcClient jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integration> all() {
        return jdbc.sql(SELECT + " ORDER BY provider").query(this::map).list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integration> find(IntegrationProvider provider) {
        return jdbc.sql(SELECT + " WHERE provider = :provider")
                .param("provider", provider.name())
                .query(this::map)
                .optional();
    }

    @Override
    @Transactional
    public void save(Integration integration) {
        jdbc.sql("""
                        INSERT INTO action_integrations (workspace_id, provider, account, username, token, enabled,
                                                         created_at, updated_at)
                        VALUES (:workspaceId, :provider, :account, :username, :token, :enabled, :createdAt,
                                :updatedAt)
                        ON CONFLICT (workspace_id, provider)
                        DO UPDATE SET account = EXCLUDED.account, username = EXCLUDED.username,
                                      token = EXCLUDED.token, enabled = EXCLUDED.enabled,
                                      updated_at = EXCLUDED.updated_at
                        """)
                .param("workspaceId", integration.workspaceId())
                .param("provider", integration.provider().name())
                .param("account", integration.account())
                .param("username", integration.username())
                .param("token", cipher.encrypt(integration.token()))
                .param("enabled", integration.enabled())
                .param("createdAt", Timestamp.from(integration.createdAt()))
                .param("updatedAt", Timestamp.from(integration.updatedAt()))
                .update();
    }

    @Override
    @Transactional
    public boolean delete(IntegrationProvider provider) {
        return jdbc.sql("DELETE FROM action_integrations WHERE provider = :provider")
                .param("provider", provider.name())
                .update() > 0;
    }

    private Integration map(ResultSet rs, int row) throws SQLException {
        return Integration.restore(rs.getObject("workspace_id", UUID.class),
                IntegrationProvider.valueOf(rs.getString("provider")), rs.getString("account"),
                rs.getString("username"), cipher.decrypt(rs.getString("token")), rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
