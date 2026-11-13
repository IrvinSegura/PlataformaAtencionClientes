package com.irvinsegura.supportchat.shared.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param encryptionKey base64 of 32 random bytes; losing it makes every stored secret unreadable
 */
@Validated
@ConfigurationProperties("supportchat.secrets")
public record SecretsProperties(@NotBlank String encryptionKey) {
}
