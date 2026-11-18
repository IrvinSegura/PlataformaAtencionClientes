package com.irvinsegura.supportchat.actions.domain.model;

import java.util.Map;
import java.util.UUID;

/**
 * One use of an integration's action.
 *
 * @param channel   where the customer writes from, e.g. {@code EMAIL}
 * @param contactId the customer's identity as the channel verified it (their email address on {@code EMAIL}), or null
 * @param arguments validated against the action's parameters
 */
public record IntegrationCall(UUID runId, UUID conversationId, String channel, String contactId,
        Map<String, Object> arguments) {

    /**
     * The customer's email address: the one the mail channel verified, else the one they gave.
     *
     * @return null when there is none
     */
    public String customerEmail() {
        if ("EMAIL".equals(channel) && contactId != null) {
            return contactId;
        }
        Object given = arguments.get("email");
        return given == null ? null : given.toString().strip();
    }
}
