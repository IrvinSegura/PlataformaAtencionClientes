package com.irvinsegura.supportchat.conversations.infrastructure.persistence;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.CustomerProfile;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.CustomerRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filtered by row-level security: both the conversations and the profiles are those of the bound workspace, so joining
 * them by channel and contact is enough.
 */
@Repository
class JdbcCustomerRepository implements CustomerRepository {

    // One row per customer, with the name and conversation of their latest activity
    private static final String CUSTOMERS = """
            WITH customers AS (
                SELECT channel, contact_id, count(*) AS conversations, min(started_at) AS first_seen_at,
                       max(last_activity_at) AS last_seen_at,
                       (array_agg(contact_name ORDER BY last_activity_at DESC, id DESC))[1] AS contact_name,
                       (array_agg(id ORDER BY last_activity_at DESC, id DESC))[1] AS latest_conversation
                FROM conversations
                WHERE contact_id IS NOT NULL%s
                GROUP BY channel, contact_id
            )
            SELECT c.*, p.display_name
            FROM customers c
            LEFT JOIN customer_profiles p ON p.channel = c.channel AND p.contact_id = c.contact_id
            """;

    private final JdbcClient jdbc;
    private final ConversationRepository conversations;

    JdbcCustomerRepository(JdbcClient jdbc, ConversationRepository conversations) {
        this.jdbc = jdbc;
        this.conversations = conversations;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerProfile> findProfile(ConversationChannel channel, String contactId) {
        return jdbc.sql("""
                        SELECT channel, contact_id, display_name, notes, remember, updated_at, updated_by_name
                        FROM customer_profiles
                        WHERE channel = :channel AND contact_id = :contactId
                        """)
                .param("channel", channel.name())
                .param("contactId", contactId)
                .query((rs, row) -> new CustomerProfile(ConversationChannel.valueOf(rs.getString("channel")),
                        rs.getString("contact_id"), rs.getString("display_name"), rs.getString("notes"),
                        rs.getBoolean("remember"), rs.getTimestamp("updated_at").toInstant(),
                        rs.getString("updated_by_name")))
                .optional();
    }

    @Override
    @Transactional
    public void saveProfile(CustomerProfile profile) {
        jdbc.sql("""
                        INSERT INTO customer_profiles (workspace_id, channel, contact_id, display_name, notes, remember,
                                                       updated_at, updated_by_name)
                        VALUES (current_workspace_id(), :channel, :contactId, :displayName, :notes, :remember,
                                :updatedAt, :updatedByName)
                        ON CONFLICT (workspace_id, channel, contact_id) DO UPDATE
                        SET display_name = EXCLUDED.display_name, notes = EXCLUDED.notes,
                            remember = EXCLUDED.remember, updated_at = EXCLUDED.updated_at,
                            updated_by_name = EXCLUDED.updated_by_name
                        """)
                .param("channel", profile.channel().name())
                .param("contactId", profile.contactId())
                .param("displayName", profile.displayName())
                .param("notes", profile.notes())
                .param("remember", profile.remember())
                .param("updatedAt", Timestamp.from(profile.updatedAt()))
                .param("updatedByName", profile.updatedByName())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversation> conversationsOf(ConversationChannel channel, String contactId, int limit) {
        List<UUID> ids = jdbc.sql("""
                        SELECT id FROM conversations
                        WHERE channel = :channel AND contact_id = :contactId
                        ORDER BY last_activity_at DESC, id DESC
                        LIMIT :limit
                        """)
                .param("channel", channel.name())
                .param("contactId", contactId)
                .param("limit", limit)
                .query(UUID.class)
                .list();
        return ids.stream().map(conversations::findById).flatMap(Optional::stream).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerSummary summaryOf(ConversationChannel channel, String contactId) {
        return jdbc.sql(CUSTOMERS.formatted(" AND channel = :channel AND contact_id = :contactId"))
                .param("channel", channel.name())
                .param("contactId", contactId)
                .query(JdbcCustomerRepository::summary)
                .single();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerPage page(String query, int page, int size) {
        String filter = query == null ? "" : """
                 WHERE c.contact_id ILIKE :pattern ESCAPE '\\' OR c.contact_name ILIKE :pattern ESCAPE '\\'
                    OR p.display_name ILIKE :pattern ESCAPE '\\'""";
        var rows = jdbc.sql(CUSTOMERS.formatted("") + filter
                        + " ORDER BY c.last_seen_at DESC, c.contact_id LIMIT :limit OFFSET :offset")
                .param("limit", size)
                .param("offset", (long) page * size);
        var count = jdbc.sql("SELECT count(*) FROM (" + CUSTOMERS.formatted("") + filter + ") matching");
        if (query != null) {
            String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            rows = rows.param("pattern", pattern);
            count = count.param("pattern", pattern);
        }
        return new CustomerPage(rows.query(JdbcCustomerRepository::summary).list(),
                count.query(Long.class).single());
    }

    private static CustomerSummary summary(ResultSet rs, int row) throws SQLException {
        return new CustomerSummary(ConversationChannel.valueOf(rs.getString("channel")), rs.getString("contact_id"),
                rs.getString("contact_name"), rs.getString("display_name"), rs.getLong("conversations"),
                rs.getTimestamp("first_seen_at").toInstant(), rs.getTimestamp("last_seen_at").toInstant(),
                rs.getObject("latest_conversation", UUID.class));
    }
}
