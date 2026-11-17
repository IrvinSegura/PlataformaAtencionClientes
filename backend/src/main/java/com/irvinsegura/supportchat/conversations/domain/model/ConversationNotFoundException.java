package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

public class ConversationNotFoundException extends NotFoundException {

    public ConversationNotFoundException() {
        super("CONVERSATION_NOT_FOUND", "Conversation not found");
    }
}
