package com.irvinsegura.supportchat.actions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The language model asked to use an action.
 *
 * @param messageId the assistant answer being written
 * @param channel   where the customer is talking from, e.g. {@code WEB} or {@code WHATSAPP}
 * @param contactId the customer's identity as the channel verified it (their WhatsApp number), or null for an
 *                  anonymous web visitor. Sent to the business so it can check the customer may see what they ask
 *                  for; the model cannot change it
 * @param arguments as the model wrote them; validated before anything is sent
 */
public record ActionInvocation(UUID conversationId, UUID messageId, String channel, String contactId,
        String actionName, Map<String, Object> arguments) {

    public ActionInvocation {
        // A null value means the model left the parameter out
        Map<String, Object> present = new LinkedHashMap<>();
        if (arguments != null) {
            arguments.forEach((key, value) -> {
                if (key != null && value != null) {
                    present.put(key, value);
                }
            });
        }
        arguments = Collections.unmodifiableMap(present);
    }
}
