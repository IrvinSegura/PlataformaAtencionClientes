package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * How well customers were helped over a period of UTC days, both ends included. Rates are shares from 0 to 1, null
 * when there is nothing to divide by.
 *
 * @param conversations       conversations started in the period in which the customer wrote
 * @param resolvedByAssistant solved by the assistant alone (see ConversationOutcome.RESOLVED)
 * @param handedOff           where a person of the team was asked for or took part
 * @param resolutionRate      resolvedByAssistant over conversations
 * @param helpfulRate         helpful verdicts over all verdicts on answers
 * @param ratingCounts        ratings given with each score, from 1 to 5
 * @param satisfaction        ratings of 4 or 5 over all ratings (CSAT)
 * @param models              the answers of each model tier and reason it was chosen for, with what customers said
 *                            of them, to tell whether a routing rule sends the fast model questions it cannot answer
 * @param difficultQuestions  questions found difficult for the first time in the period
 */
public record OutcomeReport(LocalDate from, LocalDate to, long conversations, long resolvedByAssistant, long handedOff,
        long unresolved, @Nullable Double resolutionRate, long answered, long notAnswered, long failed, long helpful,
        long unhelpful, @Nullable Double helpfulRate, long ratings, @Nullable Double averageRating,
        @Nullable Double satisfaction, List<Long> ratingCounts, List<OutcomeDay> days, List<RatingComment> comments,
        List<ModelQuality> models, long difficultQuestions) {

    public OutcomeReport {
        models = List.copyOf(models);
        ratingCounts = List.copyOf(ratingCounts);
        days = List.copyOf(days);
        comments = List.copyOf(comments);
    }

    /**
     * Conversations started that day, by outcome.
     */
    public record OutcomeDay(LocalDate day, long conversations, long resolvedByAssistant, long handedOff,
            long unresolved) {
    }

    /**
     * Answers given by one model tier for one reason.
     *
     * @param tier          the tier that answered, or null when no tier applied
     * @param reason        why the tier was chosen, as the routing names it
     * @param notAnswered   answers that said the documents did not hold the answer
     * @param unhelpfulRate unhelpful verdicts over all verdicts, null without verdicts
     */
    public record ModelQuality(@Nullable ModelTier tier, String reason, long answers, long notAnswered, long helpful,
            long unhelpful, @Nullable Double unhelpfulRate) {
    }

    public record RatingComment(UUID conversationId, int score, String comment, Instant ratedAt) {
    }
}
