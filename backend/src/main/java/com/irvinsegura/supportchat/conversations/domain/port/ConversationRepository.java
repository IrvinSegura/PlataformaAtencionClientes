package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.ConversationActivity.Activity;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationIntent;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationHandling;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Conversations of the workspace bound to the current transaction.
 */
public interface ConversationRepository {

    void insert(Conversation conversation);

    /**
     * Inserts a channel conversation unless the contact already has an open one on that channel.
     *
     * @return false when another message of the same contact won the race and started it first
     */
    boolean insertIfNoneOpen(Conversation conversation);

    /**
     * The open conversation of a contact on a channel, locked until the transaction ends.
     */
    Optional<Conversation> findOpenByContactForUpdate(ConversationChannel channel, String contactId);

    Optional<Conversation> findOpenByContact(ConversationChannel channel, String contactId);

    void update(Conversation conversation);

    /**
     * Locks the row until the transaction ends, so concurrent questions are counted one at a time.
     */
    Optional<Conversation> findByIdForUpdate(UUID id);

    Optional<Conversation> findById(UUID id);

    /**
     * @param handling only open conversations handled this way, or null for all of them
     * @param intent   only conversations about this, or null for all of them
     */
    ConversationPage page(ConversationHandling handling, ConversationIntent intent, int page, int size);

    /**
     * How many conversations started since the moment, and when the latest one moved.
     */
    Activity activitySince(Instant since);

    record ConversationPage(List<Conversation> conversations, long total) {
    }
}
