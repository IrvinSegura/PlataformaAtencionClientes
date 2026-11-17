package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

/**
 * Replying needs the conversation to be taken over first, so the assistant and a person never answer at once.
 */
public class ConversationNotTakenException extends ConflictException {

    public ConversationNotTakenException() {
        super("CONVERSATION_NOT_TAKEN", "Take the conversation over before replying");
    }
}
