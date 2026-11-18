package com.irvinsegura.supportchat.actions;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One use of an action, for the people reviewing a conversation.
 *
 * @param messageId the assistant answer that ran or proposed it
 * @param error     why it failed or was rejected, for the operator; never shown to visitors
 * @param expiresAt pending confirmations: when they stop being confirmable
 */
public record ActionRunView(UUID id, String actionName, String actionTitle, UUID messageId,
        Map<String, Object> arguments, ActionRunStatus status, @Nullable Integer httpStatus, @Nullable String error,
        @Nullable Integer latencyMs, Instant createdAt, @Nullable Instant expiresAt, @Nullable Instant finishedAt) {

    public ActionRunView {
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    /**
     * Still waiting for the visitor at the given time.
     */
    public boolean awaitsConfirmation(Instant now) {
        return status == ActionRunStatus.PENDING_CONFIRMATION && expiresAt != null && expiresAt.isAfter(now);
    }
}
