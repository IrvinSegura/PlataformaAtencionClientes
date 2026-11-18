package com.irvinsegura.supportchat.actions.infrastructure.config;

import com.irvinsegura.supportchat.actions.application.ActionSettings;
import com.irvinsegura.supportchat.actions.domain.port.ActionWebhook;
import com.irvinsegura.supportchat.actions.domain.port.EndpointPolicy;
import com.irvinsegura.supportchat.actions.domain.port.IntegrationGateway;
import com.irvinsegura.supportchat.actions.infrastructure.integration.Integrations;
import com.irvinsegura.supportchat.actions.infrastructure.webhook.ActionWebhooks;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class ActionConfiguration {

    @Bean
    ActionSettings actionSettings(ActionProperties properties) {
        return new ActionSettings(properties.confirmationTtl());
    }

    @Bean
    EndpointPolicy endpointPolicy(ActionProperties properties) {
        return ActionWebhooks.policy(properties.allowInsecureHttp(), properties.allowPrivateNetwork());
    }

    @Bean
    ActionWebhook actionWebhook(ActionProperties properties, EndpointPolicy policy, JsonMapper json) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return ActionWebhooks.http(http, json, policy, properties.requestTimeout(), properties.maxResponseBytes());
    }

    @Bean
    IntegrationGateway integrationGateway(ActionProperties properties, JsonMapper json) {
        // The providers' hosts are fixed by the integration (myshopify.com, zendesk.com): no endpoint policy needed
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        ActionProperties.IntegrationProperties endpoints = properties.integrations();
        return Integrations.gateway(http, json, new Integrations.Endpoints(endpoints.shopifyUrl(),
                endpoints.shopifyApiVersion(), endpoints.zendeskUrl()), properties.requestTimeout(),
                properties.maxResponseBytes());
    }
}
