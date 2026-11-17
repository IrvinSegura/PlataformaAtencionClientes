package com.irvinsegura.supportchat.conversations.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Something changed in a conversation that people watching it should see. It only says what changed; whoever pushes
 * it to them reads the current state, so the event stays small enough to travel between instances.
 *
 * @param messageId the new message, for {@link Type#MESSAGE_ADDED}; null otherwise
 */
public record ConversationEvent(Type type, UUID workspaceId, UUID conversationId, UUID messageId) {

    public enum Type {
        /**
         * Who answers the conversation, or whether it is open, changed.
         */
        CONVERSATION_CHANGED,
        MESSAGE_ADDED
    }

    public ConversationEvent {
        Objects.requireNonNull(type);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(conversationId);
        if ((type == Type.MESSAGE_ADDED) != (messageId != null)) {
            throw new IllegalArgumentException("Only a MESSAGE_ADDED event carries a message id");
        }
    }

    public static ConversationEvent conversationChanged(UUID workspaceId, UUID conversationId) {
        return new ConversationEvent(Type.CONVERSATION_CHANGED, workspaceId, conversationId, null);
    }

    public static ConversationEvent messageAdded(UUID workspaceId, UUID conversationId, UUID messageId) {
        return new ConversationEvent(Type.MESSAGE_ADDED, workspaceId, conversationId, messageId);
    }
}
