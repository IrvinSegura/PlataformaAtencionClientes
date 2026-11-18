package com.irvinsegura.supportchat.actions.domain.port;

import com.irvinsegura.supportchat.actions.domain.model.WebhookCall;
import com.irvinsegura.supportchat.actions.domain.model.WebhookResult;

/**
 * Calls a business's webhook. Network problems are part of the result, never thrown: the model is told and the
 * conversation goes on.
 */
public interface ActionWebhook {

    WebhookResult call(WebhookCall call);
}
