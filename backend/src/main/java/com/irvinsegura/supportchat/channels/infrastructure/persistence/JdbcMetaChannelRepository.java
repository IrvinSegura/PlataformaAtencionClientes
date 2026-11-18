package com.irvinsegura.supportchat.channels.infrastructure.persistence;

import com.irvinsegura.supportchat.channels.domain.model.MetaChannel;
import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
import com.irvinsegura.supportchat.channels.domain.port.MetaChannelRepository;
import com.irvinsegura.supportchat.shared.infrastructure.SecretCipher;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The channel rows are filtered by row-level security; the key table is not, since it is read before any workspace
 * is known, so every statement on it names the workspace explicitly.
 */
@Repository
class JdbcMetaChannelRepository implements MetaChannelRepository {

    private final JdbcClient jdbc;
    private final SecretCipher cipher;

    JdbcMetaChannelRepository(JdbcClient jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MetaChannel> find(MetaPlatform platform) {
        return jdbc.sql("""
                        SELECT c.workspace_id, c.platform, k.channel_key, c.verify_token, c.account_id, c.display_name,
                               c.access_token, c.app_secret, c.enabled, c.created_at, c.updated_at
                        FROM meta_channels c
                        JOIN meta_channel_keys k ON k.workspace_id = c.workspace_id AND k.platform = c.platform
                        WHERE c.platform = :platform
                        """)
                .param("platform", platform.name())
                .query((rs, row) -> MetaChannel.restore(
                        rs.getObject("workspace_id", UUID.class),
                        MetaPlatform.valueOf(rs.getString("platform")),
                        rs.getString("channel_key"),
                        rs.getString("verify_token"),
                        rs.getString("account_id"),
                        rs.getString("display_name"),
                        cipher.decrypt(rs.getString("access_token")),
                        cipher.decrypt(rs.getString("app_secret")),
                        rs.getBoolean("enabled"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    @Override
    @Transactional
    public void save(MetaChannel channel) {
        jdbc.sql("""
                        INSERT INTO meta_channel_keys (channel_key, workspace_id, platform)
                        VALUES (:channelKey, :workspaceId, :platform)
                        ON CONFLICT (workspace_id, platform) DO NOTHING
                        """)
                .param("channelKey", channel.channelKey())
                .param("workspaceId", channel.workspaceId())
                .param("platform", channel.platform().name())
                .update();
        jdbc.sql("""
                        INSERT INTO meta_channels (workspace_id, platform, account_id, display_name, access_token,
                                                   app_secret, verify_token, enabled, created_at, updated_at)
                        VALUES (:workspaceId, :platform, :accountId, :displayName, :accessToken, :appSecret,
                                :verifyToken, :enabled, :createdAt, :updatedAt)
                        ON CONFLICT (workspace_id, platform) DO UPDATE
                        SET account_id = EXCLUDED.account_id,
                            display_name = EXCLUDED.display_name,
                            access_token = EXCLUDED.access_token,
                            app_secret = EXCLUDED.app_secret,
                            enabled = EXCLUDED.enabled,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("workspaceId", channel.workspaceId())
                .param("platform", channel.platform().name())
                .param("accountId", channel.accountId())
                .param("displayName", channel.displayName())
                .param("accessToken", cipher.encrypt(channel.accessToken()))
                .param("appSecret", cipher.encrypt(channel.appSecret()))
                .param("verifyToken", channel.verifyToken())
                .param("enabled", channel.enabled())
                .param("createdAt", Timestamp.from(channel.createdAt()))
                .param("updatedAt", Timestamp.from(channel.updatedAt()))
                .update();
    }

    @Override
    @Transactional
    public void delete(UUID workspaceId, MetaPlatform platform) {
        jdbc.sql("DELETE FROM meta_channels WHERE platform = :platform")
                .param("platform", platform.name())
                .update();
        jdbc.sql("DELETE FROM meta_channel_keys WHERE workspace_id = :workspaceId AND platform = :platform")
                .param("workspaceId", workspaceId)
                .param("platform", platform.name())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KeyOwner> ownerOfKey(String channelKey) {
        return jdbc.sql("SELECT workspace_id, platform FROM meta_channel_keys WHERE channel_key = :channelKey")
                .param("channelKey", channelKey)
                .query((rs, row) -> new KeyOwner(rs.getObject("workspace_id", UUID.class),
                        MetaPlatform.valueOf(rs.getString("platform"))))
                .optional();
    }
}
