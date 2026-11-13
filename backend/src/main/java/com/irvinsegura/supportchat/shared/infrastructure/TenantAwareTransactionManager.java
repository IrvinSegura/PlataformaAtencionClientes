package com.irvinsegura.supportchat.shared.infrastructure;

import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Starts every transaction by copying the bound workspace into the PostgreSQL setting that row-level security
 * policies read. The setting is transaction-local, so it disappears at commit or rollback and a pooled connection
 * never carries one workspace into another's work. With no workspace bound it is set to empty, which the policies
 * treat as "no rows": a forgotten binding fails closed instead of exposing other workspaces.
 */
class TenantAwareTransactionManager extends JdbcTransactionManager {

    static final String WORKSPACE_SETTING = "app.workspace_id";

    TenantAwareTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void prepareTransactionalConnection(Connection connection, TransactionDefinition definition)
            throws SQLException {
        super.prepareTransactionalConnection(connection, definition);
        String workspaceId = TenantContext.currentWorkspaceId().map(UUID::toString).orElse("");
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config(?, ?, true)")) {
            statement.setString(1, WORKSPACE_SETTING);
            statement.setString(2, workspaceId);
            statement.execute();
        }
    }
}
