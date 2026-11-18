package com.irvinsegura.supportchat.actions.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param allowInsecureHttp   lets webhooks use plain http; development and tests only
 * @param allowPrivateNetwork lets webhooks point to local or private addresses; development and tests only
 * @param integrations        where the ready-made integrations' APIs are
 */
@Validated
@ConfigurationProperties("supportchat.actions")
public record ActionProperties(
        @NotNull Duration confirmationTtl,
        @NotNull Duration connectTimeout,
        @NotNull Duration requestTimeout,
        @Min(1024) @Max(1_048_576) int maxResponseBytes,
        boolean allowInsecureHttp,
        boolean allowPrivateNetwork,
        @NotNull @Valid IntegrationProperties integrations) {

    /**
     * @param shopifyUrl        the store's address, with {@code {store}} for its myshopify.com domain
     * @param shopifyApiVersion the Admin API version the queries are written for
     * @param zendeskUrl        the account's address, with {@code {subdomain}} for its subdomain
     */
    public record IntegrationProperties(
            @NotBlank String shopifyUrl,
            @NotBlank String shopifyApiVersion,
            @NotBlank String zendeskUrl) {
    }
}
