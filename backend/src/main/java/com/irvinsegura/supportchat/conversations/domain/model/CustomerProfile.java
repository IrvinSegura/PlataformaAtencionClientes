package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import java.time.Instant;
import java.util.Objects;

/**
 * What the team wrote about a customer who reaches the business through a channel, such as their WhatsApp number.
 * Notes are for the team only; the assistant never reads them.
 *
 * @param displayName   the name the team knows the customer by, or null to show the one the channel reports
 * @param remember      whether the assistant may read the customer's earlier conversations
 * @param updatedByName who wrote the last change, or null before anyone did
 */
public record CustomerProfile(ConversationChannel channel, String contactId, String displayName, String notes,
        boolean remember, Instant updatedAt, String updatedByName) {

    public static final int MAX_DISPLAY_NAME_LENGTH = 120;
    public static final int MAX_NOTES_LENGTH = 4000;

    public CustomerProfile {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(contactId);
        notes = notes == null ? "" : notes;
    }

    /**
     * The profile of a customer nobody wrote about yet.
     */
    public static CustomerProfile blank(ConversationChannel channel, String contactId) {
        return new CustomerProfile(channel, contactId, null, "", true, null, null);
    }

    /**
     * The profile after a member's change, validated.
     */
    public CustomerProfile edit(String displayName, String notes, boolean remember, String memberName,
            Instant now) {
        String name = displayName == null || displayName.isBlank() ? null : displayName.strip();
        String text = notes == null ? "" : notes.strip();
        if ((name != null && name.length() > MAX_DISPLAY_NAME_LENGTH) || text.length() > MAX_NOTES_LENGTH) {
            throw new InvalidCustomerProfileException(MAX_DISPLAY_NAME_LENGTH, MAX_NOTES_LENGTH);
        }
        return new CustomerProfile(channel, contactId, name, text, remember, now, memberName);
    }
}
