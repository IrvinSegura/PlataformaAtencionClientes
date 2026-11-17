package com.irvinsegura.supportchat.conversations.infrastructure.persistence;

import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Reason;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Resolution;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Status;
import com.irvinsegura.supportchat.conversations.domain.port.DifficultQuestionRepository;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Filtered by row-level security like every workspace table: no query here mentions the workspace.
 */
@Repository
class JdbcDifficultQuestionRepository implements DifficultQuestionRepository {

    private static final String COLUMNS = """
            id, question, reason, status, last_answer, conversation_id, occurrences, first_seen_at, last_seen_at, \
            resolution, alerted_at""";
    private static final String RECORDED = "id, status, occurrences, alerted_at IS NOT NULL AS alerted";
    // Open, and found difficult or opened since the given instant
    private static final String ACTIVE = """
            status = 'OPEN' AND GREATEST(last_seen_at, status_changed_at) >= :activeSince""";
    // Found difficult again: taught or closed by itself, it opens again with its run of good answers over
    private static final String FOUND_AGAIN = """
            good_answers = 0,
            status = CASE WHEN difficult_questions.status IN ('TAUGHT', 'RESOLVED') THEN 'OPEN'
                          ELSE difficult_questions.status END,
            resolution = CASE WHEN difficult_questions.status IN ('TAUGHT', 'RESOLVED') THEN NULL
                              ELSE difficult_questions.resolution END,
            status_changed_at = CASE WHEN difficult_questions.status IN ('TAUGHT', 'RESOLVED') THEN :now
                                     ELSE difficult_questions.status_changed_at END""";

    private final JdbcClient jdbc;

    JdbcDifficultQuestionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Recorded record(UUID workspaceId, UUID newId, String questionKey, String question, Set<String> words,
            float[] meaning, Reason reason, UUID conversationId, String lastAnswer, Instant now) {
        return jdbc.sql("""
                        INSERT INTO difficult_questions (id, workspace_id, question_key, question, words, embedding,
                                                         reason, status, last_answer, conversation_id, occurrences,
                                                         first_seen_at, last_seen_at, status_changed_at)
                        VALUES (:id, :workspaceId, :questionKey, :question, :words, CAST(:meaning AS vector), :reason,
                                'OPEN', :lastAnswer, :conversationId, 1, :now, :now, :now)
                        ON CONFLICT (workspace_id, question_key) DO UPDATE
                        SET question = EXCLUDED.question, words = EXCLUDED.words,
                            embedding = COALESCE(difficult_questions.embedding, EXCLUDED.embedding),
                            reason = EXCLUDED.reason,
                            last_answer = COALESCE(EXCLUDED.last_answer, difficult_questions.last_answer),
                            conversation_id = EXCLUDED.conversation_id,
                            occurrences = difficult_questions.occurrences + 1, last_seen_at = EXCLUDED.last_seen_at,
                        """ + FOUND_AGAIN + "\nRETURNING " + RECORDED)
                .param("id", newId)
                .param("workspaceId", workspaceId)
                .param("questionKey", questionKey)
                .param("question", question)
                // A String[] is sent as a PostgreSQL text[]
                .param("words", words.toArray(String[]::new))
                .param("meaning", vector(meaning))
                .param("reason", reason.name())
                .param("lastAnswer", lastAnswer)
                .param("conversationId", conversationId)
                .param("now", Timestamp.from(now))
                .query(this::recorded)
                .single();
    }

    @Override
    public Optional<Recorded> recordAgain(UUID id, String question, float[] meaning, Reason reason,
            UUID conversationId, String lastAnswer, Instant now) {
        return jdbc.sql("""
                        UPDATE difficult_questions
                        SET question = :question,
                            embedding = COALESCE(embedding, CAST(:meaning AS vector)),
                            reason = :reason,
                            last_answer = COALESCE(:lastAnswer, last_answer),
                            conversation_id = :conversationId,
                            occurrences = occurrences + 1, last_seen_at = :now,
                        """ + FOUND_AGAIN + "\nWHERE id = :id\nRETURNING " + RECORDED)
                .param("id", id)
                .param("question", question)
                .param("meaning", vector(meaning))
                .param("reason", reason.name())
                .param("lastAnswer", lastAnswer)
                .param("conversationId", conversationId)
                .param("now", Timestamp.from(now))
                .query(this::recorded)
                .optional();
    }

    @Override
    public Optional<UUID> findByKey(String questionKey) {
        return jdbc.sql("SELECT id FROM difficult_questions WHERE question_key = :questionKey")
                .param("questionKey", questionKey)
                .query(UUID.class)
                .optional();
    }

    @Override
    public List<WordsCandidate> sharingWords(Set<String> words, Instant activeSince, int limit) {
        if (words.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id, words FROM difficult_questions WHERE words && CAST(:words AS text[])"
                        + (activeSince == null ? "" : " AND " + ACTIVE)
                        + " ORDER BY last_seen_at DESC LIMIT :limit")
                .param("words", words.toArray(String[]::new))
                .param("activeSince", activeSince == null ? null : Timestamp.from(activeSince))
                .param("limit", limit)
                .query((rs, row) -> new WordsCandidate(rs.getObject("id", UUID.class), words(rs.getArray("words"))))
                .list();
    }

    @Override
    public Optional<MeaningCandidate> closestInMeaning(float[] meaning, Instant activeSince) {
        // Few questions per workspace, so scanning them beats an index shared with every other workspace
        return jdbc.sql("""
                        SELECT id, 1 - (embedding <=> CAST(:meaning AS vector)) AS similarity
                        FROM difficult_questions
                        WHERE embedding IS NOT NULL""" + (activeSince == null ? "" : " AND " + ACTIVE) + """

                        ORDER BY embedding <=> CAST(:meaning AS vector), id
                        LIMIT 1
                        """)
                .param("meaning", vector(meaning))
                .param("activeSince", activeSince == null ? null : Timestamp.from(activeSince))
                .query((rs, row) -> new MeaningCandidate(rs.getObject("id", UUID.class), rs.getDouble("similarity")))
                .optional();
    }

    @Override
    public boolean hasActive(Instant activeSince) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM difficult_questions WHERE " + ACTIVE + ")")
                .param("activeSince", Timestamp.from(activeSince))
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean answeredWell(UUID id, int toClose, Instant now) {
        // The right-hand sides read the row as it was, so all of them see the same count
        return jdbc.sql("""
                        UPDATE difficult_questions
                        SET good_answers = good_answers + 1,
                            status = CASE WHEN good_answers + 1 >= :toClose THEN 'RESOLVED' ELSE status END,
                            resolution = CASE WHEN good_answers + 1 >= :toClose THEN 'ANSWERED_WELL'
                                              ELSE resolution END,
                            status_changed_at = CASE WHEN good_answers + 1 >= :toClose THEN :now
                                                     ELSE status_changed_at END
                        WHERE id = :id AND status = 'OPEN'
                        RETURNING status
                        """)
                .param("id", id)
                .param("toClose", toClose)
                .param("now", Timestamp.from(now))
                .query(String.class)
                .optional()
                .filter(Status.RESOLVED.name()::equals)
                .isPresent();
    }

    @Override
    public int closeQuiet(Instant activeSince, Instant now) {
        return jdbc.sql("""
                        UPDATE difficult_questions
                        SET status = 'RESOLVED', resolution = 'QUIET', status_changed_at = :now
                        WHERE status = 'OPEN' AND GREATEST(last_seen_at, status_changed_at) < :activeSince
                        """)
                .param("activeSince", Timestamp.from(activeSince))
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    public boolean markAlerted(UUID id, long minOccurrences, Instant now) {
        return jdbc.sql("""
                        UPDATE difficult_questions SET alerted_at = :now
                        WHERE id = :id AND status = 'OPEN' AND occurrences >= :minOccurrences AND alerted_at IS NULL
                        """)
                .param("id", id)
                .param("minOccurrences", minOccurrences)
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    @Override
    public Optional<DifficultQuestion> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM difficult_questions WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public Page page(Status status, int page, int size) {
        // Found difficult the most first: teaching those helps the most customers
        List<DifficultQuestion> items = jdbc.sql("SELECT " + COLUMNS + """
                         FROM difficult_questions
                        WHERE status = :status
                        ORDER BY occurrences DESC, last_seen_at DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("status", status.name())
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(this::map)
                .list();
        long total = jdbc.sql("SELECT count(*) FROM difficult_questions WHERE status = :status")
                .param("status", status.name())
                .query(Long.class)
                .single();
        return new Page(items, total);
    }

    @Override
    public boolean changeStatus(UUID id, Status status, Instant now) {
        return jdbc.sql("""
                        UPDATE difficult_questions
                        SET status = :status, status_changed_at = :now, resolution = NULL,
                            good_answers = CASE WHEN :status = 'OPEN' THEN 0 ELSE good_answers END
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("status", status.name())
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    @Override
    public long countFirstSeen(Instant from, Instant to) {
        return jdbc.sql("""
                        SELECT count(*) FROM difficult_questions
                        WHERE first_seen_at >= :from AND first_seen_at < :to
                        """)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query(Long.class)
                .single();
    }

    private Recorded recorded(ResultSet rs, int row) throws SQLException {
        return new Recorded(rs.getObject("id", UUID.class), Status.valueOf(rs.getString("status")),
                rs.getLong("occurrences"), rs.getBoolean("alerted"));
    }

    private DifficultQuestion map(ResultSet rs, int row) throws SQLException {
        String resolution = rs.getString("resolution");
        Timestamp alertedAt = rs.getTimestamp("alerted_at");
        return new DifficultQuestion(
                rs.getObject("id", UUID.class),
                rs.getString("question"),
                Reason.valueOf(rs.getString("reason")),
                Status.valueOf(rs.getString("status")),
                rs.getString("last_answer"),
                rs.getObject("conversation_id", UUID.class),
                rs.getLong("occurrences"),
                rs.getTimestamp("first_seen_at").toInstant(),
                rs.getTimestamp("last_seen_at").toInstant(),
                resolution == null ? null : Resolution.valueOf(resolution),
                alertedAt == null ? null : alertedAt.toInstant());
    }

    private static Set<String> words(Array array) throws SQLException {
        return array == null ? Set.of() : new TreeSet<>(Arrays.asList((String[]) array.getArray()));
    }

    // pgvector's text format, e.g. [0.1,0.2]; null stays null
    private static String vector(float[] values) {
        if (values == null) {
            return null;
        }
        StringBuilder literal = new StringBuilder(values.length * 10).append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(values[i]);
        }
        return literal.append(']').toString();
    }
}
