package com.irvinsegura.supportchat.actions.domain.port;

import com.irvinsegura.supportchat.actions.domain.model.Integration;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationAction;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationCall;
import com.irvinsegura.supportchat.actions.domain.model.WebhookResult;

/**
 * Runs an integration's action against the provider's API. Like a webhook, network and API problems are part of the
 * result, never thrown, and the result body is what the model reads.
 */
public interface IntegrationGateway {

    WebhookResult run(Integration integration, IntegrationAction action, IntegrationCall call);
}
