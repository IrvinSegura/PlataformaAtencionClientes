package com.irvinsegura.supportchat.conversations;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A customer needs someone from the team and the workspace wants to be told, published once the conversation is
 * saved. The channels module sends the alert by WhatsApp to each recipient and reports back through
 * {@link AttentionAlerts}.
 *
 * @param contactId        the customer's identity on their channel (their number on WhatsApp); null on the web
 * @param contactName      the name the customer shows, or null
 * @param excerpt          what the customer wrote, cut short; null when they pressed a button
 * @param recipients       WhatsApp numbers to alert, digits only with the country code
 * @param template         the approved WhatsApp template to send instead of a free text, or null
 * @param templateLanguage the template's language code, e.g. {@code es_MX}; null without a template
 */
public record CustomerNeedsAttention(UUID alertId, UUID workspaceId, UUID conversationId, ConversationChannel channel,
        String contactId, String contactName, AttentionReason reason, String excerpt, List<String> recipients,
        String template, String templateLanguage, Instant occurredAt) {

    public CustomerNeedsAttention {
        Objects.requireNonNull(alertId);
        Objects.requireNonNull(workspaceId);
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(reason);
        recipients = List.copyOf(recipients);
    }
}
