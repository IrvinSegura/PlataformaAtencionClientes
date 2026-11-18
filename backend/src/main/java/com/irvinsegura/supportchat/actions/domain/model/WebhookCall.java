package com.irvinsegura.supportchat.actions.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Everything sent to the business's webhook for one run. The run id doubles as an idempotency key: a business that
 * sees it twice can skip the second one.
 *
 * @param contactId the customer's identity as the channel verified it, or null for anonymous web visitors
 */
public record WebhookCall(String url, String signingSecret, UUID runId, String actionName, UUID workspaceId,
        UUID conversationId, String channel, String contactId, Map<String, Object> arguments, Instant sentAt) {
}
