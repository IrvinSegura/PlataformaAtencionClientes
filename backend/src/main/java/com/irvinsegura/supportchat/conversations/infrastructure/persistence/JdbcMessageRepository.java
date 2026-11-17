package com.irvinsegura.supportchat.conversations.infrastructure.persistence;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.Citation;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.Message.ModelUsage;
import com.irvinsegura.supportchat.conversations.domain.model.MessageAuthor;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcMessageRepository implements MessageRepository {

    private static final String COLUMNS = """
            id, conversation_id, author, content, outcome, citations, model, input_tokens, output_tokens, latency_ms,
            author_id, author_name, created_at, model_tier, routing_reason, action_run_id""";

    private static final String M_COLUMNS = """
            m.id, m.conversation_id, m.author, m.content, m.outcome, m.citations, m.model, m.input_tokens,
            m.output_tokens, m.latency_ms, m.author_id, m.author_name, m.created_at, m.model_tier, m.routing_reason,
            m.action_run_id""";

    private static final TypeReference<List<Citation>> CITATIONS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcMessageRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public void insert(Message message) {
        ModelUsage usage = message.usage();
        jdbc.sql("""
                        INSERT INTO conversation_messages (id, workspace_id, conversation_id, author, content, outcome,
                                                           citations, model, input_tokens, output_tokens, latency_ms,
                                                           author_id, author_name, created_at, model_tier,
                                                           routing_reason, action_run_id)
                        SELECT :id, c.workspace_id, c.id, :author, :content, :outcome, CAST(:citations AS jsonb),
                               :model, :inputTokens, :outputTokens, :latencyMs, :authorId, :authorName, :createdAt,
                               :modelTier, :routingReason, :actionRunId
                        FROM conversations c
                        WHERE c.id = :conversationId
                        """)
                .param("id", message.id())
                .param("conversationId", message.conversationId())
                .param("author", message.author().name())
                .param("content", message.content())
                .param("outcome", message.outcome() == null ? null : message.outcome().name())
                .param("citations", json.writeValueAsString(message.citations()))
                .param("model", usage == null ? null : usage.model())
                .param("inputTokens", usage == null ? null : usage.inputTokens())
                .param("outputTokens", usage == null ? null : usage.outputTokens())
                .param("latencyMs", usage == null ? null : (int) Math.min(Integer.MAX_VALUE, usage.latencyMillis()))
                .param("authorId", message.authorId())
                .param("authorName", message.authorName())
                .param("createdAt", Timestamp.from(message.createdAt()))
                .param("modelTier", usage == null || usage.tier() == null ? null : usage.tier().name())
                .param("routingReason", usage == null ? null : usage.routingReason())
                .param("actionRunId", message.actionRunId())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Message> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM conversation_messages WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> recentBefore(UUID conversationId, UUID beforeMessageId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Message> newestFirst = jdbc.sql("SELECT " + COLUMNS + """
                         FROM conversation_messages
                        WHERE conversation_id = :conversationId
                          AND id <> :beforeId
                          AND created_at <= (SELECT created_at FROM conversation_messages WHERE id = :beforeId)
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit
                        """)
                .param("conversationId", conversationId)
                .param("beforeId", beforeMessageId)
                .param("limit", limit)
                .query(this::map)
                .list();
        List<Message> oldestFirst = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(oldestFirst);
        return oldestFirst;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> all(UUID conversationId) {
        return jdbc.sql("SELECT " + COLUMNS
                        + " FROM conversation_messages WHERE conversation_id = :conversationId ORDER BY created_at, id")
                .param("conversationId", conversationId)
                .query(this::map)
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> lastFromContact(ConversationChannel channel, String contactId) {
        return jdbc.sql("""
                        SELECT max(m.created_at)
                        FROM conversation_messages m
                        JOIN conversations c ON c.id = m.conversation_id
                        WHERE c.channel = :channel AND c.contact_id = :contactId AND m.author = 'VISITOR'
                        """)
                .param("channel", channel.name())
                .param("contactId", contactId)
                .query((rs, row) -> rs.getTimestamp(1))
                .optional()
                .map(Timestamp::toInstant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> earlierOfContact(ConversationChannel channel, String contactId, UUID exceptConversationId,
            Instant since, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Message> newestFirst = jdbc.sql("SELECT " + M_COLUMNS + """
                         FROM conversation_messages m
                         JOIN conversations c ON c.id = m.conversation_id
                        WHERE c.channel = :channel AND c.contact_id = :contactId AND c.id <> :exceptId
                          AND c.last_activity_at >= :since AND m.created_at >= :since
                        ORDER BY m.created_at DESC, m.id DESC
                        LIMIT :limit
                        """)
                .param("channel", channel.name())
                .param("contactId", contactId)
                .param("exceptId", exceptConversationId)
                .param("since", Timestamp.from(since))
                .param("limit", limit)
                .query(this::map)
                .list();
        List<Message> oldestFirst = new ArrayList<>(newestFirst);
        java.util.Collections.reverse(oldestFirst);
        return oldestFirst;
    }

    private Message map(ResultSet rs, int row) throws SQLException {
        String outcome = rs.getString("outcome");
        String model = rs.getString("model");
        String tier = rs.getString("model_tier");
        ModelUsage usage = model == null ? null : new ModelUsage(model, rs.getInt("input_tokens"),
                rs.getInt("output_tokens"), rs.getLong("latency_ms"), tier == null ? null : ModelTier.valueOf(tier),
                rs.getString("routing_reason"));
        return new Message(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                MessageAuthor.valueOf(rs.getString("author")),
                rs.getString("content"),
                outcome == null ? null : AnswerOutcome.valueOf(outcome),
                json.readValue(rs.getString("citations"), CITATIONS),
                usage,
                rs.getObject("author_id", UUID.class),
                rs.getString("author_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getObject("action_run_id", UUID.class));
    }
}
