package com.irvinsegura.supportchat.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.testcontainers.containers.GenericContainer;

/**
 * Two instances share one Redis: a client gets each limit once, not once per instance.
 */
class RedisRateLimitBucketsTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @BeforeAll
    static void startRedis() {
        REDIS.start();
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    private static String url() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }

    @Test
    void twoInstancesShareTheClientsTokens() throws Exception {
        RedisRateLimitBuckets first = new RedisRateLimitBuckets(url());
        RedisRateLimitBuckets second = new RedisRateLimitBuckets(url());
        String client = "login|ip:" + UUID.randomUUID();
        try {
            assertThat(first.bucket(client, 3, Duration.ofMinutes(1)).tryConsume(1)).isTrue();
            assertThat(second.bucket(client, 3, Duration.ofMinutes(1)).tryConsume(1)).isTrue();
            assertThat(first.bucket(client, 3, Duration.ofMinutes(1)).tryConsume(1)).isTrue();

            assertThat(second.bucket(client, 3, Duration.ofMinutes(1)).tryConsume(1)).isFalse();
            // Another client has its own tokens
            assertThat(second.bucket("login|ip:" + UUID.randomUUID(), 3, Duration.ofMinutes(1)).tryConsume(1))
                    .isTrue();
        } finally {
            first.destroy();
            second.destroy();
        }
    }

    @Test
    void whenTheStoreCannotBeReachedTheRequestGoesThrough() throws Exception {
        RateLimitBuckets broken = mock(RateLimitBuckets.class);
        when(broken.bucket(anyString(), anyLong(), any())).thenThrow(new IllegalStateException("Redis is down"));
        RateLimitProperties properties = new RateLimitProperties(true, RateLimitProperties.Store.REDIS, url(),
                List.of(new RateLimitProperties.Rule("auth", List.of("POST"), List.of("/api/v1/auth/**"),
                        RateLimitProperties.ClientKey.IP, 1, Duration.ofMinutes(1))));
        RateLimitFilter filter = new RateLimitFilter(properties, broken, mock(HandlerExceptionResolver.class),
                new SimpleMeterRegistry());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"), response, chain);

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
