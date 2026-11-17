package com.irvinsegura.supportchat.conversations.infrastructure.persistence;

import com.irvinsegura.supportchat.conversations.ConversationActivity.Activity;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationIntent;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationHandling;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationStatus;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.shared.domain.Language;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filtered by row-level security like every workspace table: no query here mentions the workspace.
 */
@Repository
class JdbcConversationRepository implements ConversationRepository {

    private static final String COLUMNS = """
            id, workspace_id, visitor_token_hash, status, question_count, origin, started_at, last_activity_at,
            handling, agent_id, agent_name, handoff_requested_at, channel, contact_id, contact_name, language,
            frustrated_at, intents, intents_edited""";

    private final JdbcClient jdbc;

    JdbcConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void insert(Conversation conversation) {
        insert(conversation, "");
    }

    @Override
    @Transactional
    public boolean insertIfNoneOpen(Conversation conversation) {
        // The partial unique index on open contact conversations turns a concurrent duplicate into a no-op
        return insert(conversation, " ON CONFLICT DO NOTHING") == 1;
    }

    private int insert(Conversation conversation, String onConflict) {
        return jdbc.sql("""
                        INSERT INTO conversations (id, workspace_id, visitor_token_hash, status, question_count, origin,
                                                   started_at, last_activity_at, channel, contact_id, contact_name,
                                                   language)
                        VALUES (:id, :workspaceId, :tokenHash, :status, :questionCount, :origin, :startedAt,
                                :lastActivityAt, :channel, :contactId, :contactName, :language)
                        """ + onConflict)
                .param("id", conversation.id())
                .param("workspaceId", conversation.workspaceId())
                .param("tokenHash", conversation.visitorTokenHash())
                .param("status", conversation.status().name())
                .param("questionCount", conversation.questionCount())
                .param("origin", conversation.origin())
                .param("startedAt", Timestamp.from(conversation.startedAt()))
                .param("lastActivityAt", Timestamp.from(conversation.lastActivityAt()))
                .param("channel", conversation.channel().name())
                .param("contactId", conversation.contactId())
                .param("contactName", conversation.contactName())
                .param("language", code(conversation.language()))
                .update();
    }

    @Override
    @Transactional
    public Optional<Conversation> findOpenByContactForUpdate(ConversationChannel channel, String contactId) {
        return openByContact(channel, contactId, " FOR UPDATE");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findOpenByContact(ConversationChannel channel, String contactId) {
        return openByContact(channel, contactId, "");
    }

    private Optional<Conversation> openByContact(ConversationChannel channel, String contactId, String lock) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM conversations
                        WHERE channel = :channel AND contact_id = :contactId AND status = 'OPEN'
                        """ + lock)
                .param("channel", channel.name())
                .param("contactId", contactId)
                .query(JdbcConversationRepository::map)
                .optional();
    }

    @Override
    @Transactional
    public void update(Conversation conversation) {
        jdbc.sql("""
                        UPDATE conversations
                        SET status = :status, question_count = :questionCount, last_activity_at = :lastActivityAt,
                            handling = :handling, agent_id = :agentId, agent_name = :agentName,
                            handoff_requested_at = :handoffRequestedAt, language = :language,
                            frustrated_at = :frustratedAt, intents = CAST(:intents AS text[]),
                            intents_edited = :intentsEdited
                        WHERE id = :id
                        """)
                .param("status", conversation.status().name())
                .param("questionCount", conversation.questionCount())
                .param("lastActivityAt", Timestamp.from(conversation.lastActivityAt()))
                .param("handling", conversation.handling().name())
                .param("agentId", conversation.agentId())
                .param("agentName", conversation.agentName())
                .param("handoffRequestedAt", conversation.handoffRequestedAt() == null ? null
                        : Timestamp.from(conversation.handoffRequestedAt()))
                .param("language", code(conversation.language()))
                .param("frustratedAt", conversation.frustratedAt() == null ? null
                        : Timestamp.from(conversation.frustratedAt()))
                .param("intents", intentsArray(conversation.intents()))
                .param("intentsEdited", conversation.intentsEdited())
                .param("id", conversation.id())
                .update();
    }

    @Override
    @Transactional
    public Optional<Conversation> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM conversations WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(JdbcConversationRepository::map)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM conversations WHERE id = :id")
                .param("id", id)
                .query(JdbcConversationRepository::map)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationPage page(ConversationHandling handling, ConversationIntent intent, int page, int size) {
        List<String> conditions = new ArrayList<>();
        if (handling != null) {
            // Open conversations only when filtering by handling: a closed one waits for nobody
            conditions.add("handling = :handling AND status = 'OPEN'");
        }
        if (intent != null) {
            conditions.add("intents @> ARRAY[CAST(:intent AS text)]");
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        // The waiting queue is served oldest request first, everything else most recently active first
        String order = handling == ConversationHandling.WAITING
                ? " ORDER BY handoff_requested_at, id"
                : " ORDER BY last_activity_at DESC, id DESC";
        var query = jdbc.sql("SELECT " + COLUMNS + " FROM conversations" + where + order
                        + " LIMIT :limit OFFSET :offset")
                .param("limit", size)
                .param("offset", (long) page * size);
        var count = jdbc.sql("SELECT count(*) FROM conversations" + where);
        if (handling != null) {
            query = query.param("handling", handling.name());
            count = count.param("handling", handling.name());
        }
        if (intent != null) {
            query = query.param("intent", intent.name());
            count = count.param("intent", intent.name());
        }
        List<Conversation> conversations = query.query(JdbcConversationRepository::map).list();
        long total = count.query(Long.class).single();
        return new ConversationPage(conversations, total);
    }

    private static Conversation map(ResultSet rs, int row) throws SQLException {
        return Conversation.restore(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getBytes("visitor_token_hash"),
                ConversationStatus.valueOf(rs.getString("status")),
                rs.getInt("question_count"),
                rs.getString("origin"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("last_activity_at").toInstant(),
                ConversationHandling.valueOf(rs.getString("handling")),
                rs.getObject("agent_id", UUID.class),
                rs.getString("agent_name"),
                instantOrNull(rs.getTimestamp("handoff_requested_at")),
                ConversationChannel.valueOf(rs.getString("channel")),
                rs.getString("contact_id"),
                rs.getString("contact_name"),
                Language.ofCode(rs.getString("language")).orElse(null),
                instantOrNull(rs.getTimestamp("frustrated_at")),
                intents((String[]) rs.getArray("intents").getArray()),
                rs.getBoolean("intents_edited"));
    }

    private static Set<ConversationIntent> intents(String[] names) {
        Set<ConversationIntent> intents = EnumSet.noneOf(ConversationIntent.class);
        for (String name : names) {
            intents.add(ConversationIntent.valueOf(name));
        }
        return intents;
    }

    // PostgreSQL's text form of an array; the names are enum constants, so they need no quoting
    private static String intentsArray(Set<ConversationIntent> intents) {
        return intents.stream().map(Enum::name).sorted().collect(Collectors.joining(",", "{", "}"));
    }

    private static String code(Language language) {
        return language == null ? null : language.code();
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @Override
    public Activity activitySince(Instant since) {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE started_at >= :since) AS started,
                               max(last_activity_at) AS last_activity_at
                        FROM conversations
                        """)
                .param("since", Timestamp.from(since))
                .query((rs, row) -> {
                    Timestamp last = rs.getTimestamp("last_activity_at");
                    return new Activity(rs.getLong("started"), last == null ? null : last.toInstant());
                })
                .single();
    }
}
