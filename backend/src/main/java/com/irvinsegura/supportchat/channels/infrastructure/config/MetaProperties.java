package com.irvinsegura.supportchat.channels.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Messenger and Instagram.
 *
 * @param graphBaseUrl the Graph API with its version, e.g. {@code https://graph.facebook.com/v23.0}
 */
@Validated
@ConfigurationProperties("supportchat.channels.meta")
public record MetaProperties(@NotBlank String graphBaseUrl, @NotNull Duration connectTimeout,
        @NotNull Duration requestTimeout) {
}
