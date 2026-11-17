package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.CustomerProfile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Customers of the workspace bound to the current transaction who wrote through a channel, and what the team wrote
 * about them.
 */
public interface CustomerRepository {

    /**
     * @return empty while nobody wrote anything about the customer
     */
    Optional<CustomerProfile> findProfile(ConversationChannel channel, String contactId);

    /**
     * Inserts the profile or replaces the saved one.
     */
    void saveProfile(CustomerProfile profile);

    /**
     * The customer's conversations, most recently active first.
     */
    List<Conversation> conversationsOf(ConversationChannel channel, String contactId, int limit);

    /**
     * How many conversations the customer had and when.
     */
    CustomerSummary summaryOf(ConversationChannel channel, String contactId);

    /**
     * Customers most recently active first.
     *
     * @param query part of the contact (e.g. digits of the number) or of a name, or null for all of them
     */
    CustomerPage page(String query, int page, int size);

    /**
     * @param contactName        the name the channel reported in the latest conversation, or null
     * @param displayName        the name the team gave the customer, or null
     * @param latestConversation the conversation the customer was last active in
     */
    record CustomerSummary(ConversationChannel channel, String contactId, String contactName, String displayName,
            long conversations, Instant firstSeenAt, Instant lastSeenAt, UUID latestConversation) {
    }

    record CustomerPage(List<CustomerSummary> customers, long total) {
    }
}
