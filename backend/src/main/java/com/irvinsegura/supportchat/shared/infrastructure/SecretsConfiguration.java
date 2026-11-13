package com.irvinsegura.supportchat.shared.infrastructure;

import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SecretsConfiguration {

    @Bean
    SecretCipher secretCipher(SecretsProperties properties) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.encryptionKey().strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SECRETS_ENCRYPTION_KEY must be base64 (openssl rand -base64 32)", e);
        }
        return new SecretCipher(key);
    }
}
