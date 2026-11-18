package com.irvinsegura.supportchat.actions.domain.model;

/**
 * How the business's webhook answered.
 *
 * @param httpStatus null when there was no answer (timeout, refused connection, blocked address)
 * @param body       the start of the response body, or null
 * @param error      why it failed, for the operator; null on success
 * @param message    a message the business asked to show the customer (a {@code message} text field in a JSON
 *                   answer), or null
 */
public record WebhookResult(Integer httpStatus, String body, String error, String message, long latencyMillis) {

    public boolean succeeded() {
        return httpStatus != null && httpStatus >= 200 && httpStatus < 300;
    }

    public static WebhookResult unreachable(String error, long latencyMillis) {
        return new WebhookResult(null, null, error, null, latencyMillis);
    }
}
