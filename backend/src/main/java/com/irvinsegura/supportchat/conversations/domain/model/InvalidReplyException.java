package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class InvalidReplyException extends DomainException {

    public InvalidReplyException(int maxLength) {
        super("INVALID_REPLY", "Replies must have between 1 and %d characters".formatted(maxLength));
    }
}
