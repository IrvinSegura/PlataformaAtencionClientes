package com.irvinsegura.supportchat.actions.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A system the platform knows how to call on its own, so a workspace connects it with a token instead of writing a
 * webhook.
 */
public enum IntegrationProvider {

    // The store's Admin API (GraphQL), with the access token of a custom app installed in the store
    SHOPIFY,
    // Zendesk Support's API, as an agent with an API token
    ZENDESK;

    /**
     * The name in URLs: {@code shopify} or {@code zendesk}.
     */
    public String pathName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<IntegrationProvider> ofPathName(String name) {
        return Arrays.stream(values()).filter(provider -> provider.pathName().equals(name)).findFirst();
    }
}
