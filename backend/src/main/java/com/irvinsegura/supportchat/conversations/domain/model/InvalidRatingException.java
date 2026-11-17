package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidRatingException extends DomainException {

    public InvalidRatingException() {
        super("INVALID_RATING", "A rating is a score from 1 to 5 and a comment of at most %d characters"
                .formatted(ConversationRating.MAX_COMMENT_LENGTH));
    }
}
