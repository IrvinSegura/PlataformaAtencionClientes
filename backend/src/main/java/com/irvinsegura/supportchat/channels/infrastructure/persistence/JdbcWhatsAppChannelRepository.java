package com.irvinsegura.supportchat.channels.infrastructure.persistence;

import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppChannelRepository;
import com.irvinsegura.supportchat.conversations.ModelPreference;
import com.irvinsegura.supportchat.shared.infrastructure.SecretCipher;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The channel row is filtered by row-level security; the key table is not, since it is read before any workspace is
 * known, so every statement on it names the workspace explicitly.
 */
@Repository
class JdbcWhatsAppChannelRepository implements WhatsAppChannelRepository {

    private final JdbcClient jdbc;
    private final SecretCipher cipher;

    JdbcWhatsAppChannelRepository(JdbcClient jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WhatsAppChannel> find() {
        return jdbc.sql("""
                        SELECT c.workspace_id, k.channel_key, c.verify_token, c.phone_number_id, c.display_phone_number,
                               c.access_token, c.app_secret, c.enabled, c.model, c.created_at, c.updated_at
                        FROM whatsapp_channels c
                        JOIN whatsapp_channel_keys k ON k.workspace_id = c.workspace_id
                        """)
                .query((rs, row) -> WhatsAppChannel.restore(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getString("channel_key"),
                        rs.getString("verify_token"),
                        rs.getString("phone_number_id"),
                        rs.getString("display_phone_number"),
                        cipher.decrypt(rs.getString("access_token")),
                        cipher.decrypt(rs.getString("app_secret")),
                        rs.getBoolean("enabled"),
                        ModelPreference.valueOf(rs.getString("model")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    @Override
    @Transactional
    public void save(WhatsAppChannel channel) {
        jdbc.sql("""
                        INSERT INTO whatsapp_channel_keys (channel_key, workspace_id)
                        VALUES (:channelKey, :workspaceId)
                        ON CONFLICT (workspace_id) DO NOTHING
                        """)
                .param("channelKey", channel.channelKey())
                .param("workspaceId", channel.workspaceId())
                .update();
        jdbc.sql("""
                        INSERT INTO whatsapp_channels (workspace_id, phone_number_id, display_phone_number, access_token,
                                                       app_secret, verify_token, enabled, model, created_at, updated_at)
                        VALUES (:workspaceId, :phoneNumberId, :displayPhoneNumber, :accessToken, :appSecret,
                                :verifyToken, :enabled, :model, :createdAt, :updatedAt)
                        ON CONFLICT (workspace_id) DO UPDATE
                        SET phone_number_id = EXCLUDED.phone_number_id,
                            display_phone_number = EXCLUDED.display_phone_number,
                            access_token = EXCLUDED.access_token,
                            app_secret = EXCLUDED.app_secret,
                            enabled = EXCLUDED.enabled,
                            model = EXCLUDED.model,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("workspaceId", channel.workspaceId())
                .param("phoneNumberId", channel.phoneNumberId())
                .param("displayPhoneNumber", channel.displayPhoneNumber())
                .param("accessToken", cipher.encrypt(channel.accessToken()))
                .param("appSecret", cipher.encrypt(channel.appSecret()))
                .param("verifyToken", channel.verifyToken())
                .param("enabled", channel.enabled())
                .param("model", channel.model().name())
                .param("createdAt", Timestamp.from(channel.createdAt()))
                .param("updatedAt", Timestamp.from(channel.updatedAt()))
                .update();
    }

    @Override
    @Transactional
    public void delete(UUID workspaceId) {
        jdbc.sql("DELETE FROM whatsapp_channels").update();
        jdbc.sql("DELETE FROM whatsapp_channel_keys WHERE workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> workspaceOfKey(String channelKey) {
        return jdbc.sql("SELECT workspace_id FROM whatsapp_channel_keys WHERE channel_key = :channelKey")
                .param("channelKey", channelKey)
                .query(UUID.class)
                .optional();
    }

    @Override
    @Transactional
    public boolean firstDelivery(UUID workspaceId, String messageId, Instant receivedAt) {
        return jdbc.sql("""
                        INSERT INTO whatsapp_inbound_messages (workspace_id, message_id, received_at)
                        VALUES (:workspaceId, :messageId, :receivedAt)
                        ON CONFLICT DO NOTHING
                        """)
                .param("workspaceId", workspaceId)
                .param("messageId", messageId)
                .param("receivedAt", Timestamp.from(receivedAt))
                .update() == 1;
    }
}
