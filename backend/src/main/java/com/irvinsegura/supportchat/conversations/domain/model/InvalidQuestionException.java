package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidQuestionException extends DomainException {

    public InvalidQuestionException(int maxLength) {
        super("INVALID_QUESTION", "Questions must have between 1 and %d characters".formatted(maxLength));
    }
}
