package com.irvinsegura.supportchat.actions;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What happened when the assistant used an action.
 *
 * @param runId          null when nothing was recorded
 * @param forModel       what to tell the language model: the business's answer, or why there is none
 * @param visitorMessage a message the business's system asked to show the customer, or null
 * @param expiresAt      pending confirmations: when they stop being confirmable; null otherwise
 */
public record ActionOutcome(UUID runId, ActionRunStatus status, String title, Map<String, Object> arguments,
        String forModel, String visitorMessage, Instant expiresAt) {

    public ActionOutcome {
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    public boolean isPendingConfirmation() {
        return status == ActionRunStatus.PENDING_CONFIRMATION;
    }

    public boolean succeeded() {
        return status == ActionRunStatus.SUCCEEDED;
    }
}
