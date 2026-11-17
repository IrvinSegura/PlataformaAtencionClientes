package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.DomainException;

public class ConversationClosedException extends DomainException {

    public ConversationClosedException() {
        super("CONVERSATION_CLOSED", "This conversation is closed; start a new one");
    }
}
