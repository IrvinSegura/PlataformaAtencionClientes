package com.irvinsegura.supportchat.conversations.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * How the customer rated a conversation, from 1 (very bad) to 5 (very good), with an optional comment.
 */
public record ConversationRating(UUID conversationId, int score, String comment, Instant ratedAt) {

    public static final int MAX_COMMENT_LENGTH = 1_000;

    public ConversationRating {
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(ratedAt);
        if (score < 1 || score > 5) {
            throw new InvalidRatingException();
        }
        comment = comment == null || comment.isBlank() ? null : comment.strip();
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new InvalidRatingException();
        }
    }
}
