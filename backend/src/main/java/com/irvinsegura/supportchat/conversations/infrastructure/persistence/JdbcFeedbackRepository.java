package com.irvinsegura.supportchat.conversations.infrastructure.persistence;

import com.irvinsegura.supportchat.conversations.domain.model.ConversationRating;
import com.irvinsegura.supportchat.conversations.domain.port.FeedbackRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFeedbackRepository implements FeedbackRepository {

    private final JdbcClient jdbc;

    JdbcFeedbackRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveAnswerFeedback(UUID workspaceId, UUID conversationId, UUID messageId, boolean helpful,
            Instant givenAt) {
        jdbc.sql("""
                        INSERT INTO answer_feedback (message_id, workspace_id, conversation_id, helpful, given_at)
                        VALUES (:messageId, :workspaceId, :conversationId, :helpful, :givenAt)
                        ON CONFLICT (message_id) DO UPDATE SET helpful = EXCLUDED.helpful, given_at = EXCLUDED.given_at
                        """)
                .param("messageId", messageId)
                .param("workspaceId", workspaceId)
                .param("conversationId", conversationId)
                .param("helpful", helpful)
                .param("givenAt", Timestamp.from(givenAt))
                .update();
    }

    @Override
    public Map<UUID, Boolean> answerFeedback(UUID conversationId) {
        Map<UUID, Boolean> verdicts = new HashMap<>();
        jdbc.sql("SELECT message_id, helpful FROM answer_feedback WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .query((rs, row) -> verdicts.put(rs.getObject("message_id", UUID.class), rs.getBoolean("helpful")))
                .list();
        return verdicts;
    }

    @Override
    public void saveRating(UUID workspaceId, ConversationRating rating) {
        jdbc.sql("""
                        INSERT INTO conversation_ratings (conversation_id, workspace_id, score, comment, rated_at)
                        VALUES (:conversationId, :workspaceId, :score, :comment, :ratedAt)
                        ON CONFLICT (conversation_id) DO UPDATE
                        SET score = EXCLUDED.score, comment = EXCLUDED.comment, rated_at = EXCLUDED.rated_at
                        """)
                .param("conversationId", rating.conversationId())
                .param("workspaceId", workspaceId)
                .param("score", rating.score())
                .param("comment", rating.comment())
                .param("ratedAt", Timestamp.from(rating.ratedAt()))
                .update();
    }

    @Override
    public Optional<ConversationRating> rating(UUID conversationId) {
        return jdbc.sql("""
                        SELECT conversation_id, score, comment, rated_at FROM conversation_ratings
                        WHERE conversation_id = :conversationId
                        """)
                .param("conversationId", conversationId)
                .query((rs, row) -> new ConversationRating(rs.getObject("conversation_id", UUID.class),
                        rs.getInt("score"), rs.getString("comment"), rs.getTimestamp("rated_at").toInstant()))
                .optional();
    }
}
