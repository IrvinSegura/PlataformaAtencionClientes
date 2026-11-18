package com.irvinsegura.supportchat.actions.infrastructure.webhook;

import com.irvinsegura.supportchat.actions.domain.port.ActionWebhook;
import com.irvinsegura.supportchat.actions.domain.port.EndpointPolicy;
import java.net.http.HttpClient;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Factory for the webhook adapters, which stay package-private.
 */
public final class ActionWebhooks {

    private ActionWebhooks() {
    }

    public static EndpointPolicy policy(boolean allowInsecureHttp, boolean allowPrivateNetwork) {
        return new NetworkEndpointPolicy(allowInsecureHttp, allowPrivateNetwork);
    }

    public static ActionWebhook http(HttpClient http, JsonMapper json, EndpointPolicy policy, Duration requestTimeout,
            int maxResponseBytes) {
        return new HttpActionWebhook(http, json, policy, requestTimeout, maxResponseBytes);
    }
}
