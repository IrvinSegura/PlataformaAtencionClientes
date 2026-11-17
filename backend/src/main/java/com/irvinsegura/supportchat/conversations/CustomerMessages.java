package com.irvinsegura.supportchat.conversations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Messages a member writes to the customer of a conversation outside of it, such as a scheduled follow-up, for the
 * workspace bound to the current code. Every method runs in the caller's transaction.
 */
public interface CustomerMessages {

    /**
     * The longest text a member's message keeps; {@link #sendAsMember} cuts a longer one.
     */
    int MAX_TEXT_LENGTH = 4_000;

    /**
     * The customer a conversation is with, or empty when the workspace has no such conversation.
     */
    Optional<Contact> contactOf(UUID conversationId);

    /**
     * Where a message to the customer of a conversation goes now: their open conversation on the same channel, so the
     * team sees the message and the answer to it together, or the given one. The conversation stays locked until the
     * transaction ends.
     *
     * @return empty when the workspace has no such conversation
     */
    Optional<Recipient> recipientOf(UUID conversationId);

    /**
     * Saves a member's message in a conversation and sends it to the customer through its channel, cut to the longest
     * reply a conversation keeps.
     *
     * @param template WhatsApp only: the approved template to send instead of the text, or null
     * @return the message's id
     */
    UUID sendAsMember(UUID conversationId, UUID memberId, String memberName, String text,
            ConversationReply.Template template, Instant at);

    /**
     * @param contactId   the customer's identity on the channel, e.g. their phone number; for the widget, the visitor's
     * @param contactName the name the customer shows on the channel, or null
     */
    record Contact(UUID conversationId, ConversationChannel channel, String contactId, String contactName) {
    }

    /**
     * @param customerName  the customer's name as the team saved it or the channel shows it; null when unknown
     * @param lastMessageAt when the customer last wrote on the channel, or null if never
     */
    record Recipient(UUID conversationId, ConversationChannel channel, String customerName, Instant lastMessageAt) {
    }
}
