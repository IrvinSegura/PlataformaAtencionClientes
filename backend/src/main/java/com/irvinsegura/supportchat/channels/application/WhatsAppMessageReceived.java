package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.conversations.ModelPreference;
import java.util.UUID;

/**
 * A new WhatsApp message was accepted from a webhook delivery; it is answered once the delivery is acknowledged, so
 * Meta gets its quick answer and does not deliver it again.
 *
 * @param text     what the contact wrote, or null when they pressed a button
 * @param buttonId the button they pressed, or null
 * @param model    the model the number answers with; null (events saved before it existed) lets the platform pick
 */
public record WhatsAppMessageReceived(UUID workspaceId, String contactId, String contactName, String text,
        String buttonId, ModelPreference model) {

    public WhatsAppMessageReceived(UUID workspaceId, String contactId, String contactName, String text,
            String buttonId) {
        this(workspaceId, contactId, contactName, text, buttonId, null);
    }
}
