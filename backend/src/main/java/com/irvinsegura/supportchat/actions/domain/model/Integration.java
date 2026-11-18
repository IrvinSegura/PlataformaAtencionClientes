package com.irvinsegura.supportchat.actions.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A system a workspace connected with its own credentials, whose actions the assistant may then use.
 * <p>
 * The account names the workspace's instance, and the address the platform calls is built from it on the provider's
 * own domain ({@code <store>.myshopify.com}, {@code <subdomain>.zendesk.com}), so an integration can never point the
 * platform anywhere else. The token is never shown again once saved.
 */
public final class Integration {

    private static final Pattern SHOPIFY_STORE = Pattern.compile("[a-z0-9][a-z0-9-]{0,59}\\.myshopify\\.com");
    private static final Pattern ZENDESK_SUBDOMAIN = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{1,63}");
    private static final Pattern SCHEME = Pattern.compile("^https?://");
    private static final int MAX_TOKEN = 512;

    private final UUID workspaceId;
    private final IntegrationProvider provider;
    private final Instant createdAt;
    private String account;
    private String username;
    private String token;
    private boolean enabled;
    private Instant updatedAt;

    private Integration(UUID workspaceId, IntegrationProvider provider, String account, String username,
            String token, boolean enabled, Instant createdAt, Instant updatedAt) {
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.provider = Objects.requireNonNull(provider);
        this.account = account;
        this.username = username;
        this.token = token;
        this.enabled = enabled;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static Integration connect(UUID workspaceId, IntegrationProvider provider, Settings settings,
            Instant now) {
        if (blank(settings.token())) {
            throw new InvalidIntegrationException("The access token is required");
        }
        Integration integration = new Integration(workspaceId, provider, null, null, null, false, now, now);
        integration.change(settings, now);
        return integration;
    }

    public static Integration restore(UUID workspaceId, IntegrationProvider provider, String account,
            String username, String token, boolean enabled, Instant createdAt, Instant updatedAt) {
        return new Integration(workspaceId, provider, account, username, token, enabled, createdAt, updatedAt);
    }

    /**
     * A blank token keeps the saved one.
     */
    public void change(Settings settings, Instant now) {
        String newAccount = switch (provider) {
            case SHOPIFY -> shopifyStore(settings.account());
            case ZENDESK -> zendeskSubdomain(settings.account());
        };
        String newUsername = null;
        if (provider == IntegrationProvider.ZENDESK) {
            newUsername = settings.username() == null ? "" : settings.username().strip().toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(newUsername).matches()) {
                throw new InvalidIntegrationException("Zendesk needs the email address of the agent the API token "
                        + "belongs to");
            }
        }
        if (!blank(settings.token()) && (settings.token().strip().length() > MAX_TOKEN
                || settings.token().strip().chars().anyMatch(Character::isWhitespace))) {
            throw new InvalidIntegrationException("The access token is not valid");
        }
        account = newAccount;
        username = newUsername;
        if (!blank(settings.token())) {
            token = settings.token().strip();
        }
        enabled = settings.enabled();
        updatedAt = now;
    }

    // "mitienda", "mitienda.myshopify.com" and "https://mitienda.myshopify.com/admin" all name the same store
    private static String shopifyStore(String value) {
        String store = host(value);
        if (!store.isEmpty() && !store.contains(".")) {
            store = store + ".myshopify.com";
        }
        if (!SHOPIFY_STORE.matcher(store).matches()) {
            throw new InvalidIntegrationException("The store is its myshopify.com address, e.g. mitienda.myshopify.com");
        }
        return store;
    }

    // "mitienda" and "mitienda.zendesk.com" name the same account
    private static String zendeskSubdomain(String value) {
        String subdomain = host(value);
        if (subdomain.endsWith(".zendesk.com")) {
            subdomain = subdomain.substring(0, subdomain.length() - ".zendesk.com".length());
        }
        if (!ZENDESK_SUBDOMAIN.matcher(subdomain).matches()) {
            throw new InvalidIntegrationException("The Zendesk account is its subdomain, e.g. mitienda for "
                    + "mitienda.zendesk.com");
        }
        return subdomain;
    }

    private static String host(String value) {
        String host = SCHEME.matcher(value == null ? "" : value.strip().toLowerCase(Locale.ROOT)).replaceFirst("");
        int slash = host.indexOf('/');
        return slash < 0 ? host : host.substring(0, slash);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public IntegrationProvider provider() {
        return provider;
    }

    /**
     * The store's myshopify.com domain, or the Zendesk subdomain.
     */
    public String account() {
        return account;
    }

    /**
     * The Zendesk agent's email address; null for Shopify.
     */
    public String username() {
        return username;
    }

    public String token() {
        return token;
    }

    public boolean enabled() {
        return enabled;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * @param account  the Shopify store or the Zendesk subdomain
     * @param username the Zendesk agent's email; ignored for Shopify
     * @param token    the Shopify Admin API access token or the Zendesk API token; blank keeps the saved one
     */
    public record Settings(String account, String username, String token, boolean enabled) {

        @Override
        public String toString() {
            // The token stays out of logs
            return "Settings[account=" + account + ", enabled=" + enabled + "]";
        }
    }
}
