package com.irvinsegura.supportchat.conversations.infrastructure.persistence;

import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.port.OutcomeRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Counted in the database: a period holds thousands of conversations, and only the totals travel.
 */
@Repository
class JdbcOutcomeRepository implements OutcomeRepository {

    private final JdbcClient jdbc;

    JdbcOutcomeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DayOutcome> conversations(Instant from, Instant to) {
        // The rules of ConversationOutcome; a team member is involved once a handoff was asked for, the plan
        // paused the assistant or an agent wrote
        return jdbc.sql("""
                        WITH scoped AS (
                            SELECT (c.started_at AT TIME ZONE 'UTC')::date AS day,
                                   EXISTS (SELECT 1 FROM conversation_messages m
                                           WHERE m.conversation_id = c.id
                                             AND (m.author = 'AGENT'
                                                  OR m.author = 'SYSTEM' AND m.content IN
                                                     ('HANDOFF_REQUESTED', 'ASSISTANT_PAUSED', 'AGENT_JOINED')))
                                       AS human,
                                   (SELECT m.outcome FROM conversation_messages m
                                    WHERE m.conversation_id = c.id AND m.author = 'ASSISTANT'
                                      AND m.outcome IS NOT NULL AND m.outcome <> 'SMALL_TALK'
                                    ORDER BY m.created_at DESC LIMIT 1) AS last_outcome,
                                   (SELECT f.helpful FROM answer_feedback f
                                    WHERE f.conversation_id = c.id
                                    ORDER BY f.given_at DESC LIMIT 1) AS last_verdict,
                                   r.score
                            FROM conversations c
                            LEFT JOIN conversation_ratings r ON r.conversation_id = c.id
                            WHERE c.started_at >= :from AND c.started_at < :to
                              AND EXISTS (SELECT 1 FROM conversation_messages q
                                          WHERE q.conversation_id = c.id AND q.author = 'VISITOR')
                        )
                        SELECT day,
                               CASE WHEN human THEN 'HANDED_OFF'
                                    WHEN last_outcome = 'ANSWERED' AND COALESCE(last_verdict, TRUE)
                                         AND COALESCE(score, 5) >= 3 THEN 'RESOLVED'
                                    ELSE 'UNRESOLVED' END AS outcome,
                               count(*) AS conversations
                        FROM scoped
                        GROUP BY 1, 2
                        ORDER BY 1, 2
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, row) -> new DayOutcome(rs.getObject("day", LocalDate.class),
                        ConversationOutcome.valueOf(rs.getString("outcome")), rs.getLong("conversations")))
                .list();
    }

    @Override
    public Map<AnswerOutcome, Long> answers(Instant from, Instant to) {
        Map<AnswerOutcome, Long> counts = new EnumMap<>(AnswerOutcome.class);
        jdbc.sql("""
                        SELECT outcome, count(*) AS answers FROM conversation_messages
                        WHERE author = 'ASSISTANT' AND outcome IS NOT NULL
                          AND created_at >= :from AND created_at < :to
                        GROUP BY outcome
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, row) -> counts.put(AnswerOutcome.valueOf(rs.getString("outcome")), rs.getLong("answers")))
                .list();
        return counts;
    }

    @Override
    public List<ModelAnswers> modelAnswers(Instant from, Instant to) {
        // Replies no model was asked for (courtesy, a "no sé" without sources) carry no reason; reused ones say so
        return jdbc.sql("""
                        SELECT m.model_tier, m.routing_reason, count(*) AS answers,
                               count(*) FILTER (WHERE m.outcome = 'NO_ANSWER') AS not_answered,
                               count(*) FILTER (WHERE f.helpful) AS helpful,
                               count(*) FILTER (WHERE NOT f.helpful) AS unhelpful
                        FROM conversation_messages m
                        LEFT JOIN answer_feedback f ON f.message_id = m.id
                        WHERE m.author = 'ASSISTANT' AND m.outcome IN ('ANSWERED', 'NO_ANSWER')
                          AND m.routing_reason IS NOT NULL
                          AND m.created_at >= :from AND m.created_at < :to
                        GROUP BY 1, 2
                        ORDER BY answers DESC
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, row) -> new ModelAnswers(
                        rs.getString("model_tier") == null ? null : ModelTier.valueOf(rs.getString("model_tier")),
                        rs.getString("routing_reason"),
                        rs.getLong("answers"), rs.getLong("not_answered"), rs.getLong("helpful"),
                        rs.getLong("unhelpful")))
                .list();
    }

    @Override
    public Map<Boolean, Long> answerFeedback(Instant from, Instant to) {
        Map<Boolean, Long> counts = new HashMap<>();
        jdbc.sql("""
                        SELECT helpful, count(*) AS verdicts FROM answer_feedback
                        WHERE given_at >= :from AND given_at < :to
                        GROUP BY helpful
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, row) -> counts.put(rs.getBoolean("helpful"), rs.getLong("verdicts")))
                .list();
        return counts;
    }

    @Override
    public Map<Integer, Long> ratings(Instant from, Instant to) {
        Map<Integer, Long> counts = new HashMap<>();
        jdbc.sql("""
                        SELECT score, count(*) AS ratings FROM conversation_ratings
                        WHERE rated_at >= :from AND rated_at < :to
                        GROUP BY score
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((rs, row) -> counts.put(rs.getInt("score"), rs.getLong("ratings")))
                .list();
        return counts;
    }

    @Override
    public List<RatingComment> comments(Instant from, Instant to, int limit) {
        return jdbc.sql("""
                        SELECT conversation_id, score, comment, rated_at FROM conversation_ratings
                        WHERE comment IS NOT NULL AND rated_at >= :from AND rated_at < :to
                        ORDER BY rated_at DESC
                        LIMIT :limit
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .param("limit", limit)
                .query((rs, row) -> new RatingComment(rs.getObject("conversation_id", UUID.class),
                        rs.getInt("score"), rs.getString("comment"), rs.getTimestamp("rated_at").toInstant()))
                .list();
    }
}
