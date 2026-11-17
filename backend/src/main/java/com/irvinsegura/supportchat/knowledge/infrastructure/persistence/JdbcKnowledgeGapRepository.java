package com.irvinsegura.supportchat.knowledge.infrastructure.persistence;

import com.irvinsegura.supportchat.knowledge.domain.model.GapStatus;
import com.irvinsegura.supportchat.knowledge.domain.port.KnowledgeGapRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcKnowledgeGapRepository implements KnowledgeGapRepository {

    private final JdbcClient jdbc;

    JdbcKnowledgeGapRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void recordOccurrence(UUID workspaceId, UUID newGapId, String questionKey, String question,
            UUID conversationId, UUID messageId, Instant askedAt) {
        // Concurrent first askings of one question meet at the unique key instead of creating two gaps
        UUID gapId = jdbc.sql("""
                        INSERT INTO knowledge_gaps (id, workspace_id, question_key, question, status, status_changed_at,
                                                    created_at)
                        VALUES (:id, :workspaceId, :key, :question, 'OPEN', :askedAt, :askedAt)
                        ON CONFLICT (workspace_id, question_key) DO UPDATE SET question = EXCLUDED.question
                        RETURNING id
                        """)
                .param("id", newGapId)
                .param("workspaceId", workspaceId)
                .param("key", questionKey)
                .param("question", question)
                .param("askedAt", Timestamp.from(askedAt))
                .query(UUID.class)
                .single();
        int inserted = jdbc.sql("""
                        INSERT INTO knowledge_gap_occurrences (message_id, workspace_id, gap_id, conversation_id,
                                                               asked_at)
                        VALUES (:messageId, :workspaceId, :gapId, :conversationId, :askedAt)
                        ON CONFLICT (message_id) DO NOTHING
                        """)
                .param("messageId", messageId)
                .param("workspaceId", workspaceId)
                .param("gapId", gapId)
                .param("conversationId", conversationId)
                .param("askedAt", Timestamp.from(askedAt))
                .update();
        if (inserted == 1) {
            jdbc.sql("""
                            UPDATE knowledge_gaps SET status = 'OPEN', status_changed_at = :askedAt
                            WHERE id = :gapId AND status = 'RESOLVED' AND status_changed_at < :askedAt
                            """)
                    .param("gapId", gapId)
                    .param("askedAt", Timestamp.from(askedAt))
                    .update();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GapPage page(GapStatus status, int page, int size) {
        // Most asked first: those are the answers that would help the most visitors
        List<GapSummary> gaps = jdbc.sql("""
                        SELECT g.id, g.question, g.status, count(o.message_id) AS occurrences,
                               min(o.asked_at) AS first_asked_at, max(o.asked_at) AS last_asked_at,
                               (SELECT o2.conversation_id FROM knowledge_gap_occurrences o2
                                WHERE o2.gap_id = g.id ORDER BY o2.asked_at DESC LIMIT 1) AS last_conversation_id
                        FROM knowledge_gaps g
                        JOIN knowledge_gap_occurrences o ON o.gap_id = g.id
                        WHERE g.status = :status
                        GROUP BY g.id
                        ORDER BY occurrences DESC, last_asked_at DESC, g.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", status.name())
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(JdbcKnowledgeGapRepository::map)
                .list();
        long total = jdbc.sql("SELECT count(*) FROM knowledge_gaps WHERE status = :status")
                .param("status", status.name())
                .query(Long.class)
                .single();
        return new GapPage(gaps, total);
    }

    @Override
    @Transactional
    public boolean changeStatus(UUID gapId, GapStatus status, Instant now) {
        return jdbc.sql("UPDATE knowledge_gaps SET status = :status, status_changed_at = :now WHERE id = :id")
                .param("status", status.name())
                .param("now", Timestamp.from(now))
                .param("id", gapId)
                .update() == 1;
    }

    @Override
    public Optional<String> question(UUID gapId) {
        return jdbc.sql("SELECT question FROM knowledge_gaps WHERE id = :id")
                .param("id", gapId)
                .query(String.class)
                .optional();
    }

    private static GapSummary map(ResultSet rs, int row) throws SQLException {
        return new GapSummary(
                rs.getObject("id", UUID.class),
                rs.getString("question"),
                GapStatus.valueOf(rs.getString("status")),
                rs.getLong("occurrences"),
                rs.getTimestamp("first_asked_at").toInstant(),
                rs.getTimestamp("last_asked_at").toInstant(),
                rs.getObject("last_conversation_id", UUID.class));
    }
}
