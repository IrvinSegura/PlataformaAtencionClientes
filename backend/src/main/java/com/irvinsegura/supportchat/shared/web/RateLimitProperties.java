package com.irvinsegura.supportchat.shared.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rules are checked in order and the first one matching a request applies, so specific rules go before broad ones.
 *
 * @param store    where the buckets live; memory unless set
 * @param redisUrl with the Redis store, e.g. {@code redis://redis:6379}
 */
@Validated
@ConfigurationProperties("supportchat.rate-limit")
public record RateLimitProperties(boolean enabled, Store store, String redisUrl, List<@Valid Rule> rules) {

    public RateLimitProperties {
        store = store == null ? Store.MEMORY : store;
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (enabled && store == Store.REDIS && (redisUrl == null || redisUrl.isBlank())) {
            throw new IllegalArgumentException("supportchat.rate-limit.redis-url is required with the redis store");
        }
    }

    public enum Store {
        // Each instance counts on its own: a client gets the limit once per instance
        MEMORY,
        // Shared by every instance
        REDIS
    }

    /**
     * Allows {@code capacity} requests per {@code period} for each client, refilled gradually.
     *
     * @param methods HTTP methods the rule applies to; empty means all
     * @param paths   path patterns, e.g. {@code /api/v1/auth/**}
     */
    public record Rule(
            @NotBlank String name,
            List<String> methods,
            @NotEmpty List<String> paths,
            @NotNull ClientKey key,
            @Min(1) long capacity,
            @NotNull Duration period) {

        public Rule {
            methods = methods == null ? List.of() : List.copyOf(methods);
        }
    }

    public enum ClientKey {
        // Client address; the only option for anonymous callers
        IP,
        // Authenticated operator, falling back to the address for anonymous requests
        USER,
        // The widget key in /api/v1/widget/{key}/..., i.e. one bucket shared by all visitors of a workspace
        WIDGET
    }
}
