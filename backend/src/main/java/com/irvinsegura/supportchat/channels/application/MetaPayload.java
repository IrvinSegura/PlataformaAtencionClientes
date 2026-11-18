package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Reads the messages out of a Messenger Platform webhook delivery, for Messenger and Instagram alike
 * (https://developers.facebook.com/docs/messenger-platform/webhooks). Echoes of the Page's own messages, read and
 * delivery receipts, reactions and attachments are skipped.
 */
final class MetaPayload {

    private MetaPayload() {
    }

    static List<Delivered> read(JsonNode root, MetaPlatform platform) {
        if (!platform.webhookObject().equals(root.path("object").asString(""))) {
            return List.of();
        }
        List<Delivered> delivered = new ArrayList<>();
        for (JsonNode entry : root.path("entry")) {
            String accountId = entry.path("id").asString("");
            for (JsonNode event : entry.path("messaging")) {
                String sender = event.path("sender").path("id").asString("");
                if (sender.isEmpty() || sender.equals(accountId)) {
                    continue;
                }
                JsonNode message = event.path("message");
                JsonNode postback = event.path("postback");
                if (!message.isMissingNode()) {
                    if (message.path("is_echo").asBoolean(false) || message.path("is_deleted").asBoolean(false)) {
                        continue;
                    }
                    String id = message.path("mid").asString("");
                    // A quick reply also carries its title as text; the payload says which one was pressed
                    String payload = message.path("quick_reply").path("payload").asString(null);
                    String text = payload == null ? message.path("text").asString(null) : null;
                    if (!id.isEmpty() && (text != null || payload != null)) {
                        delivered.add(new Delivered(accountId, id, sender, text, payload));
                    }
                } else if (!postback.isMissingNode()) {
                    String id = postback.path("mid").asString("");
                    String payload = postback.path("payload").asString(null);
                    if (!id.isEmpty() && payload != null) {
                        delivered.add(new Delivered(accountId, id, sender, null, payload));
                    }
                }
            }
        }
        return delivered;
    }

    /**
     * @param accountId the Page or Instagram account the message was sent to
     * @param senderId  the customer's id, scoped to that Page or account
     * @param text      what they wrote, or null when they pressed a button
     * @param payload   the payload of the button they pressed, or null
     */
    record Delivered(String accountId, String messageId, String senderId, String text, String payload) {
    }
}
