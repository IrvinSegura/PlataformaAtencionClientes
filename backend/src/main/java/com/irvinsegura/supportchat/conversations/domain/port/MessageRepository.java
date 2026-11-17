package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Messages of the workspace bound to the current transaction.
 */
public interface MessageRepository {

    void insert(Message message);

    Optional<Message> findById(UUID id);

    /**
     * The latest {@code limit} messages before {@code beforeMessageId}, oldest first.
     */
    List<Message> recentBefore(UUID conversationId, UUID beforeMessageId, int limit);

    /**
     * The whole conversation, oldest first.
     */
    List<Message> all(UUID conversationId);

    /**
     * The latest {@code limit} messages of a contact's other conversations since a moment, oldest first: what the
     * assistant remembers of them.
     */
    /**
     * When the contact last wrote on the channel, in any of their conversations.
     */
    Optional<Instant> lastFromContact(ConversationChannel channel, String contactId);

    List<Message> earlierOfContact(ConversationChannel channel, String contactId, UUID exceptConversationId,
            Instant since, int limit);
}
