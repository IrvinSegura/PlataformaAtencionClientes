package com.irvinsegura.supportchat.channels.infrastructure.persistence;

import com.irvinsegura.supportchat.channels.domain.port.InboundMessageLog;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcInboundMessageLog implements InboundMessageLog {

    private final JdbcClient jdbc;

    JdbcInboundMessageLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean firstDelivery(UUID workspaceId, String channel, String messageId, Instant receivedAt) {
        return jdbc.sql("""
                        INSERT INTO channel_inbound_messages (workspace_id, channel, message_id, received_at)
                        VALUES (:workspaceId, :channel, :messageId, :receivedAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("workspaceId", workspaceId)
                .param("channel", channel)
                .param("messageId", messageId)
                .param("receivedAt", Timestamp.from(receivedAt))
                .update() == 1;
    }
}
