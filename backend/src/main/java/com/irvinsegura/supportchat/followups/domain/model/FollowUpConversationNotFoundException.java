package com.irvinsegura.supportchat.followups.domain.model;

import com.irvinsegura.supportchat.shared.domain.NotFoundException;

/**
 * The conversation to schedule from or list the follow-ups of is not in the workspace; the same code the conversations
 * module answers with.
 */
public class FollowUpConversationNotFoundException extends NotFoundException {

    public FollowUpConversationNotFoundException() {
        super("CONVERSATION_NOT_FOUND", "Conversation not found");
    }
}
