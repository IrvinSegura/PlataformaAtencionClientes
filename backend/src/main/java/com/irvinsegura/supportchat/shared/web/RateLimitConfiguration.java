package com.irvinsegura.supportchat.shared.web;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "supportchat.rate-limit.enabled", havingValue = "true")
class RateLimitConfiguration {

    @Bean
    RateLimitBuckets rateLimitBuckets(RateLimitProperties properties) {
        if (properties.store() == RateLimitProperties.Store.REDIS) {
            return new RedisRateLimitBuckets(properties.redisUrl());
        }
        Duration longestPeriod = properties.rules().stream()
                .map(RateLimitProperties.Rule::period)
                .max(Comparator.naturalOrder())
                .orElse(Duration.ofMinutes(1));
        return new MemoryRateLimitBuckets(longestPeriod);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties properties, RateLimitBuckets buckets,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            MeterRegistry meterRegistry) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
                new RateLimitFilter(properties, buckets, exceptionResolver, meterRegistry));
        // Right after the security filter chain, so the authenticated operator is already known
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
        return registration;
    }
}
