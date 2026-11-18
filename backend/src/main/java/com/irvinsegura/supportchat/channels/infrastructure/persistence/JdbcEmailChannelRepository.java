package com.irvinsegura.supportchat.channels.infrastructure.persistence;

import com.irvinsegura.supportchat.channels.domain.model.EmailChannel;
import com.irvinsegura.supportchat.channels.domain.model.EmailChannel.Server;
import com.irvinsegura.supportchat.channels.domain.model.EmailThread;
import com.irvinsegura.supportchat.channels.domain.model.MailboxCursor;
import com.irvinsegura.supportchat.channels.domain.port.EmailChannelRepository;
import com.irvinsegura.supportchat.shared.infrastructure.SecretCipher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The mailbox and thread rows are filtered by row-level security; the list of workspaces with a mailbox is not,
 * since the mail job reads it before any workspace is bound, so every statement on it names the workspace.
 */
@Repository
class JdbcEmailChannelRepository implements EmailChannelRepository {

    private final JdbcClient jdbc;
    private final SecretCipher cipher;

    JdbcEmailChannelRepository(JdbcClient jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailChannel> find() {
        return jdbc.sql("""
                        SELECT workspace_id, address, display_name, imap_host, imap_port, smtp_host, smtp_port, username,
                               password, enabled, uid_validity, last_uid, checked_at, last_error, created_at, updated_at
                        FROM email_channels
                        """)
                .query((rs, row) -> EmailChannel.restore(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getString("address"),
                        rs.getString("display_name"),
                        new Server(rs.getString("imap_host"), rs.getInt("imap_port")),
                        new Server(rs.getString("smtp_host"), rs.getInt("smtp_port")),
                        rs.getString("username"),
                        cipher.decrypt(rs.getString("password")),
                        rs.getBoolean("enabled"),
                        cursor(rs),
                        instant(rs, "checked_at"),
                        rs.getString("last_error"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    private static MailboxCursor cursor(ResultSet rs) throws SQLException {
        long validity = rs.getLong("uid_validity");
        if (rs.wasNull()) {
            return null;
        }
        return new MailboxCursor(validity, rs.getLong("last_uid"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    @Override
    @Transactional
    public void save(EmailChannel channel) {
        jdbc.sql("""
                        INSERT INTO email_channel_workspaces (workspace_id) VALUES (:workspaceId)
                        ON CONFLICT DO NOTHING
                        """)
                .param("workspaceId", channel.workspaceId())
                .update();
        Optional<MailboxCursor> cursor = channel.cursor();
        jdbc.sql("""
                        INSERT INTO email_channels (workspace_id, address, display_name, imap_host, imap_port, smtp_host,
                                                    smtp_port, username, password, enabled, uid_validity, last_uid,
                                                    checked_at, last_error, created_at, updated_at)
                        VALUES (:workspaceId, :address, :displayName, :imapHost, :imapPort, :smtpHost, :smtpPort,
                                :username, :password, :enabled, :uidValidity, :lastUid, :checkedAt, :lastError,
                                :createdAt, :updatedAt)
                        ON CONFLICT (workspace_id) DO UPDATE
                        SET address = EXCLUDED.address,
                            display_name = EXCLUDED.display_name,
                            imap_host = EXCLUDED.imap_host,
                            imap_port = EXCLUDED.imap_port,
                            smtp_host = EXCLUDED.smtp_host,
                            smtp_port = EXCLUDED.smtp_port,
                            username = EXCLUDED.username,
                            password = EXCLUDED.password,
                            enabled = EXCLUDED.enabled,
                            uid_validity = EXCLUDED.uid_validity,
                            last_uid = EXCLUDED.last_uid,
                            last_error = EXCLUDED.last_error,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("workspaceId", channel.workspaceId())
                .param("address", channel.address())
                .param("displayName", channel.displayName())
                .param("imapHost", channel.imap().host())
                .param("imapPort", channel.imap().port())
                .param("smtpHost", channel.smtp().host())
                .param("smtpPort", channel.smtp().port())
                .param("username", channel.username())
                .param("password", cipher.encrypt(channel.password()))
                .param("enabled", channel.enabled())
                .param("uidValidity", cursor.map(MailboxCursor::uidValidity).orElse(null))
                .param("lastUid", cursor.map(MailboxCursor::lastUid).orElse(null))
                .param("checkedAt", channel.checkedAt() == null ? null : Timestamp.from(channel.checkedAt()))
                .param("lastError", channel.lastError())
                .param("createdAt", Timestamp.from(channel.createdAt()))
                .param("updatedAt", Timestamp.from(channel.updatedAt()))
                .update();
    }

    @Override
    @Transactional
    public void saveProgress(EmailChannel channel) {
        Optional<MailboxCursor> cursor = channel.cursor();
        jdbc.sql("""
                        UPDATE email_channels
                        SET uid_validity = :uidValidity, last_uid = :lastUid, checked_at = :checkedAt,
                            last_error = :lastError
                        WHERE workspace_id = :workspaceId
                          -- Settings saved meanwhile may point to another mailbox, which starts afresh
                          AND imap_host = :imapHost AND imap_port = :imapPort AND username = :username
                        """)
                .param("workspaceId", channel.workspaceId())
                .param("uidValidity", cursor.map(MailboxCursor::uidValidity).orElse(null))
                .param("lastUid", cursor.map(MailboxCursor::lastUid).orElse(null))
                .param("checkedAt", channel.checkedAt() == null ? null : Timestamp.from(channel.checkedAt()))
                .param("lastError", channel.lastError())
                .param("imapHost", channel.imap().host())
                .param("imapPort", channel.imap().port())
                .param("username", channel.username())
                .update();
    }

    @Override
    @Transactional
    public void delete(UUID workspaceId) {
        jdbc.sql("DELETE FROM email_channels").update();
        jdbc.sql("DELETE FROM email_threads").update();
        jdbc.sql("DELETE FROM email_channel_workspaces WHERE workspace_id = :workspaceId")
                .param("workspaceId", workspaceId)
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> workspacesWithMailbox() {
        return jdbc.sql("SELECT workspace_id FROM email_channel_workspaces ORDER BY workspace_id")
                .query(UUID.class)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailThread> findThread(String contactAddress) {
        return jdbc.sql("""
                        SELECT contact_address, subject, last_message_id, reference_ids
                        FROM email_threads
                        WHERE contact_address = :contactAddress
                        """)
                .param("contactAddress", contactAddress)
                .query((rs, row) -> new EmailThread(rs.getString("contact_address"), rs.getString("subject"),
                        rs.getString("last_message_id"), references(rs.getString("reference_ids"))))
                .optional();
    }

    private static List<String> references(String stored) {
        return stored == null || stored.isBlank() ? List.of() : Arrays.asList(stored.strip().split("\\s+"));
    }

    @Override
    @Transactional
    public void saveThread(UUID workspaceId, EmailThread thread, Instant now) {
        jdbc.sql("""
                        INSERT INTO email_threads (workspace_id, contact_address, subject, last_message_id, reference_ids,
                                                   updated_at)
                        VALUES (:workspaceId, :contactAddress, :subject, :lastMessageId, :references, :updatedAt)
                        ON CONFLICT (workspace_id, contact_address) DO UPDATE
                        SET subject = EXCLUDED.subject,
                            last_message_id = EXCLUDED.last_message_id,
                            reference_ids = EXCLUDED.reference_ids,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("workspaceId", workspaceId)
                .param("contactAddress", thread.contactAddress())
                .param("subject", thread.subject())
                .param("lastMessageId", thread.lastMessageId())
                .param("references", String.join(" ", thread.references()))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }
}
