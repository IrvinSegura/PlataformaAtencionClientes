package com.irvinsegura.supportchat.actions.domain.model;

import com.irvinsegura.supportchat.actions.ActionRunStatus;
import com.irvinsegura.supportchat.actions.ActionRunView;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One use of an action in a conversation, from the model's request to the business's answer.
 * <p>
 * A run that needs confirmation starts pending and can be confirmed exactly once: confirming moves it to running
 * before the webhook is called, so a second click (or a second device) finds it already handled.
 */
public final class ActionRun {

    public static final int MAX_RESULT_LENGTH = 4_000;
    public static final int MAX_ERROR_LENGTH = 500;

    private final UUID id;
    private final UUID actionId;
    private final String actionName;
    private final String actionTitle;
    private final UUID conversationId;
    private final UUID messageId;
    private final Map<String, Object> arguments;
    private ActionRunStatus status;
    private Integer httpStatus;
    private String result;
    private String error;
    private Integer latencyMs;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant finishedAt;

    private ActionRun(UUID id, UUID actionId, String actionName, String actionTitle, UUID conversationId,
            UUID messageId, Map<String, Object> arguments, ActionRunStatus status, Integer httpStatus, String result,
            String error, Integer latencyMs, Instant createdAt, Instant expiresAt, Instant finishedAt) {
        this.id = Objects.requireNonNull(id);
        this.actionId = actionId;
        this.actionName = Objects.requireNonNull(actionName);
        this.actionTitle = Objects.requireNonNull(actionTitle);
        this.conversationId = Objects.requireNonNull(conversationId);
        this.messageId = Objects.requireNonNull(messageId);
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        this.status = Objects.requireNonNull(status);
        this.httpStatus = httpStatus;
        this.result = result;
        this.error = error;
        this.latencyMs = latencyMs;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = expiresAt;
        this.finishedAt = finishedAt;
    }

    /**
     * The model asked for something that could not be sent.
     */
    public static ActionRun rejected(UUID id, UUID actionId, String actionName, String actionTitle,
            UUID conversationId, UUID messageId, Map<String, Object> arguments, String reason, Instant now) {
        return new ActionRun(id, actionId, actionName, actionTitle, conversationId, messageId, arguments,
                ActionRunStatus.REJECTED, null, null, truncate(reason, MAX_ERROR_LENGTH), null, now, null, now);
    }

    public static ActionRun pending(UUID id, Action action, UUID conversationId, UUID messageId,
            Map<String, Object> arguments, Duration confirmationTtl, Instant now) {
        return pending(id, action.id(), action.name(), action.title(), conversationId, messageId, arguments,
                confirmationTtl, now);
    }

    /**
     * @param actionId the workspace's own action, or null for an integration's
     */
    public static ActionRun pending(UUID id, UUID actionId, String actionName, String actionTitle,
            UUID conversationId, UUID messageId, Map<String, Object> arguments, Duration confirmationTtl,
            Instant now) {
        return new ActionRun(id, actionId, actionName, actionTitle, conversationId, messageId, arguments,
                ActionRunStatus.PENDING_CONFIRMATION, null, null, null, null, now, now.plus(confirmationTtl), null);
    }

    /**
     * An action that needs no confirmation, recorded once its webhook answered.
     */
    public static ActionRun executed(UUID id, Action action, UUID conversationId, UUID messageId,
            Map<String, Object> arguments, Instant startedAt, WebhookResult webhook, Instant now) {
        return executed(id, action.id(), action.name(), action.title(), conversationId, messageId, arguments,
                startedAt, webhook, now);
    }

    /**
     * @param actionId the workspace's own action, or null for an integration's
     */
    public static ActionRun executed(UUID id, UUID actionId, String actionName, String actionTitle,
            UUID conversationId, UUID messageId, Map<String, Object> arguments, Instant startedAt,
            WebhookResult webhook, Instant now) {
        ActionRun run = new ActionRun(id, actionId, actionName, actionTitle, conversationId, messageId, arguments,
                ActionRunStatus.RUNNING, null, null, null, null, startedAt, null, null);
        run.finish(webhook, now);
        return run;
    }

    public static ActionRun restore(UUID id, UUID actionId, String actionName, String actionTitle,
            UUID conversationId, UUID messageId, Map<String, Object> arguments, ActionRunStatus status,
            Integer httpStatus, String result, String error, Integer latencyMs, Instant createdAt, Instant expiresAt,
            Instant finishedAt) {
        return new ActionRun(id, actionId, actionName, actionTitle, conversationId, messageId, arguments, status,
                httpStatus, result, error, latencyMs, createdAt, expiresAt, finishedAt);
    }

    /**
     * The visitor confirmed. Returns false, after marking the run expired, when they took too long.
     *
     * @throws ActionAlreadyHandledException if it was confirmed, cancelled or expired before
     */
    public boolean confirm(Instant now) {
        requirePending();
        if (!expiresAt.isAfter(now)) {
            status = ActionRunStatus.EXPIRED;
            finishedAt = now;
            return false;
        }
        status = ActionRunStatus.RUNNING;
        return true;
    }

    public void cancel(Instant now) {
        requirePending();
        status = expiresAt.isAfter(now) ? ActionRunStatus.CANCELLED : ActionRunStatus.EXPIRED;
        finishedAt = now;
    }

    /**
     * Records the business's answer to a running action.
     */
    public void finish(WebhookResult webhook, Instant now) {
        if (status != ActionRunStatus.RUNNING) {
            throw new IllegalStateException("Only a running action can finish");
        }
        status = webhook.succeeded() ? ActionRunStatus.SUCCEEDED : ActionRunStatus.FAILED;
        httpStatus = webhook.httpStatus();
        result = truncate(webhook.body(), MAX_RESULT_LENGTH);
        error = truncate(webhook.error(), MAX_ERROR_LENGTH);
        latencyMs = (int) Math.min(Integer.MAX_VALUE, webhook.latencyMillis());
        finishedAt = now;
    }

    private void requirePending() {
        if (status != ActionRunStatus.PENDING_CONFIRMATION) {
            throw new ActionAlreadyHandledException();
        }
    }

    public ActionRunView view() {
        return new ActionRunView(id, actionName, actionTitle, messageId, arguments, status, httpStatus, error,
                latencyMs, createdAt, expiresAt, finishedAt);
    }

    private static String truncate(String text, int maxLength) {
        return text == null || text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    public UUID id() {
        return id;
    }

    public UUID actionId() {
        return actionId;
    }

    public String actionName() {
        return actionName;
    }

    public String actionTitle() {
        return actionTitle;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public UUID messageId() {
        return messageId;
    }

    public Map<String, Object> arguments() {
        return arguments;
    }

    public ActionRunStatus status() {
        return status;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public String result() {
        return result;
    }

    public String error() {
        return error;
    }

    public Integer latencyMs() {
        return latencyMs;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }
}
