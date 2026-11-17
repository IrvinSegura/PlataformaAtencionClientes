package com.irvinsegura.supportchat.identity.infrastructure.config;

import com.irvinsegura.supportchat.identity.domain.model.LockoutPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {

    @Bean
    LockoutPolicy lockoutPolicy(IdentityProperties properties) {
        return new LockoutPolicy(properties.lockout().maxFailedAttempts(), properties.lockout().duration());
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder(IdentityProperties properties) {
        return new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, properties.passwordHashStrength());
    }
}
