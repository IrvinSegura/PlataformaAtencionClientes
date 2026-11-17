package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Figures of the workspace bound to the current transaction over {@code [from, to)}.
 */
public interface OutcomeRepository {

    /**
     * Conversations started in the period in which the customer wrote at least once, by UTC day and outcome (see
     * {@link ConversationOutcome} for the rules).
     */
    List<DayOutcome> conversations(Instant from, Instant to);

    /**
     * Answers of the assistant given in the period, by outcome.
     */
    Map<AnswerOutcome, Long> answers(Instant from, Instant to);

    /**
     * Verdicts on answers given in the period: true for helpful.
     */
    Map<Boolean, Long> answerFeedback(Instant from, Instant to);

    /**
     * Ratings given in the period, by score.
     */
    Map<Integer, Long> ratings(Instant from, Instant to);

    /**
     * The latest ratings with a comment, newest first.
     */
    List<RatingComment> comments(Instant from, Instant to, int limit);

    /**
     * Answers written by a model in the period, by tier and reason it was chosen for, with their verdicts.
     */
    List<ModelAnswers> modelAnswers(Instant from, Instant to);

    /**
     * @param tier null when no tier applied
     */
    record ModelAnswers(ModelTier tier, String reason, long answers, long notAnswered, long helpful, long unhelpful) {
    }

    record DayOutcome(LocalDate day, ConversationOutcome outcome, long conversations) {
    }

    record RatingComment(UUID conversationId, int score, String comment, Instant ratedAt) {
    }
}
