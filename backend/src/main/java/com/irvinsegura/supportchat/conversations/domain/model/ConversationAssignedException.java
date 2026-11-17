package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.shared.domain.ConflictException;

/**
 * Another member is answering this conversation.
 */
public class ConversationAssignedException extends ConflictException {

    public ConversationAssignedException() {
        super("CONVERSATION_ASSIGNED", "Another member of the team is answering this conversation");
    }
}
