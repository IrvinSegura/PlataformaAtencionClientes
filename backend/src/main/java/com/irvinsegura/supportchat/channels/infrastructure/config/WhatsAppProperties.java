package com.irvinsegura.supportchat.channels.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param graphBaseUrl the Graph API with its version, e.g. {@code https://graph.facebook.com/v23.0}
 * @param panelUrl     the operator panel, linked from the alerts sent to the owner
 */
@Validated
@ConfigurationProperties("supportchat.channels.whatsapp")
public record WhatsAppProperties(@NotBlank String graphBaseUrl, @NotNull Duration connectTimeout,
        @NotNull Duration requestTimeout, @NotBlank String panelUrl) {
}
