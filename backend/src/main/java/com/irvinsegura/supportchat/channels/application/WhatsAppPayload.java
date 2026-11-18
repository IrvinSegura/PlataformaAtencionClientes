package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.domain.model.InboundWhatsApp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Reads the messages out of a WhatsApp Cloud API webhook delivery
 * (https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/components). Delivery receipts and message types
 * the assistant cannot read (images, audio, locations) are skipped.
 */
final class WhatsAppPayload {

    private WhatsAppPayload() {
    }

    static List<Delivered> read(JsonNode root) {
        List<Delivered> delivered = new ArrayList<>();
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                if (!"messages".equals(change.path("field").asString(""))) {
                    continue;
                }
                JsonNode value = change.path("value");
                String phoneNumberId = value.path("metadata").path("phone_number_id").asString("");
                Map<String, String> names = new HashMap<>();
                for (JsonNode contact : value.path("contacts")) {
                    names.put(contact.path("wa_id").asString(""), contact.path("profile").path("name").asString(null));
                }
                for (JsonNode message : value.path("messages")) {
                    String id = message.path("id").asString("");
                    String from = message.path("from").asString("");
                    if (id.isEmpty() || from.isEmpty()) {
                        continue;
                    }
                    String text = null;
                    String buttonId = null;
                    switch (message.path("type").asString("")) {
                        case "text" -> text = message.path("text").path("body").asString(null);
                        case "interactive" -> buttonId = message.path("interactive").path("button_reply")
                                .path("id").asString(null);
                        case "button" -> buttonId = message.path("button").path("payload").asString(null);
                        default -> {
                            // Media, locations, reactions: nothing the assistant can answer
                        }
                    }
                    if (text != null || buttonId != null) {
                        delivered.add(new Delivered(phoneNumberId,
                                new InboundWhatsApp(id, from, names.get(from), text, buttonId)));
                    }
                }
            }
        }
        return delivered;
    }

    /**
     * @param phoneNumberId the business number the message was sent to
     */
    record Delivered(String phoneNumberId, InboundWhatsApp message) {
    }
}
