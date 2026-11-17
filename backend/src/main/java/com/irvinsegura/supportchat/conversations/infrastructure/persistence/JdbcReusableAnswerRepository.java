package com.irvinsegura.supportchat.conversations.infrastructure.persistence;

import com.irvinsegura.supportchat.conversations.domain.model.Citation;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.model.ReusableAnswer;
import com.irvinsegura.supportchat.conversations.domain.port.ReusableAnswerRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcReusableAnswerRepository implements ReusableAnswerRepository {

    private static final TypeReference<List<Citation>> CITATIONS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    JdbcReusableAnswerRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<ReusableAnswer> find(String questionKey, String preference, String contextDigest,
            Instant notBefore) {
        return jdbc.sql("""
                        SELECT question_key, preference, context_digest, answer, citations, model, model_tier,
                               created_at
                        FROM reusable_answers
                        WHERE question_key = :questionKey AND preference = :preference
                          AND context_digest = :contextDigest AND created_at >= :notBefore
                        """)
                .param("questionKey", questionKey)
                .param("preference", preference)
                .param("contextDigest", contextDigest)
                .param("notBefore", Timestamp.from(notBefore))
                .query(this::map)
                .optional();
    }

    @Override
    public void save(UUID workspaceId, ReusableAnswer answer) {
        jdbc.sql("""
                        INSERT INTO reusable_answers (workspace_id, question_key, preference, context_digest, answer,
                                                      citations, model, model_tier, created_at)
                        VALUES (:workspaceId, :questionKey, :preference, :contextDigest, :answer,
                                CAST(:citations AS jsonb), :model, :tier, :createdAt)
                        ON CONFLICT (workspace_id, question_key, preference, context_digest) DO UPDATE
                        SET answer = EXCLUDED.answer, citations = EXCLUDED.citations, model = EXCLUDED.model,
                            model_tier = EXCLUDED.model_tier, created_at = EXCLUDED.created_at, reuses = 0,
                            last_reused_at = NULL
                        """)
                .param("workspaceId", workspaceId)
                .param("questionKey", answer.questionKey())
                .param("preference", answer.preference())
                .param("contextDigest", answer.contextDigest())
                .param("answer", answer.text())
                .param("citations", json.writeValueAsString(answer.citations()))
                .param("model", answer.model())
                .param("tier", answer.tier() == null ? null : answer.tier().name())
                .param("createdAt", Timestamp.from(answer.createdAt()))
                .update();
    }

    @Override
    public void markReused(String questionKey, String preference, String contextDigest, Instant now) {
        jdbc.sql("""
                        UPDATE reusable_answers SET reuses = reuses + 1, last_reused_at = :now
                        WHERE question_key = :questionKey AND preference = :preference
                          AND context_digest = :contextDigest
                        """)
                .param("questionKey", questionKey)
                .param("preference", preference)
                .param("contextDigest", contextDigest)
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    public int forget(String text) {
        return jdbc.sql("DELETE FROM reusable_answers WHERE answer = :text").param("text", text).update();
    }

    @Override
    public int deleteWrittenBefore(Instant instant) {
        return jdbc.sql("DELETE FROM reusable_answers WHERE created_at < :instant")
                .param("instant", Timestamp.from(instant))
                .update();
    }

    private ReusableAnswer map(ResultSet rs, int row) throws SQLException {
        String tier = rs.getString("model_tier");
        return new ReusableAnswer(
                rs.getString("question_key"),
                rs.getString("preference"),
                rs.getString("context_digest"),
                rs.getString("answer"),
                json.readValue(rs.getString("citations"), CITATIONS),
                rs.getString("model"),
                tier == null ? null : ModelTier.valueOf(tier),
                rs.getTimestamp("created_at").toInstant());
    }
}
