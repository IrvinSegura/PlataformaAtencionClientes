package com.irvinsegura.supportchat.actions.infrastructure.integration;

import com.irvinsegura.supportchat.actions.domain.model.Integration;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationAction;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationCall;
import com.irvinsegura.supportchat.actions.domain.model.WebhookResult;
import com.irvinsegura.supportchat.actions.domain.port.IntegrationGateway;
import java.net.http.HttpClient;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs each integration's actions with its provider's client. The clients stay package-private; this is the way in.
 */
public final class Integrations implements IntegrationGateway {

    private final ShopifyClient shopify;
    private final ZendeskClient zendesk;

    private Integrations(ShopifyClient shopify, ZendeskClient zendesk) {
        this.shopify = shopify;
        this.zendesk = zendesk;
    }

    /**
     * @param endpoints where each provider's API is; tests point them to a local server
     */
    public static IntegrationGateway gateway(HttpClient http, JsonMapper json, Endpoints endpoints,
            Duration requestTimeout, int maxResponseBytes) {
        IntegrationHttp client = new IntegrationHttp(http, json, requestTimeout, maxResponseBytes);
        return new Integrations(new ShopifyClient(client, endpoints.shopifyUrl(), endpoints.shopifyApiVersion()),
                new ZendeskClient(client, endpoints.zendeskUrl()));
    }

    @Override
    public WebhookResult run(Integration integration, IntegrationAction action, IntegrationCall call) {
        return switch (action) {
            case SHOPIFY_ORDER_STATUS -> shopify.orderStatus(integration, call);
            case SHOPIFY_CANCEL_ORDER -> shopify.cancelOrder(integration, call);
            case ZENDESK_CREATE_TICKET -> zendesk.createTicket(integration, call);
        };
    }

    /**
     * @param shopifyUrl        the store's address, with {@code {store}} for its myshopify.com domain
     * @param shopifyApiVersion the Admin API version, e.g. {@code 2025-07}
     * @param zendeskUrl        the account's address, with {@code {subdomain}} for its subdomain
     */
    public record Endpoints(String shopifyUrl, String shopifyApiVersion, String zendeskUrl) {
    }
}
