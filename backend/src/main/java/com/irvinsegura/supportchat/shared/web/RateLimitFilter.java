package com.irvinsegura.supportchat.shared.web;

import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Token-bucket rate limiting per client and rule. Registered right after the security filter chain, so the
 * authenticated operator is known and requests the chain already rejected cost nothing here.
 * <p>
 * Buckets live in this instance's memory, or in Redis with {@code supportchat.rate-limit.store=redis} so that every
 * instance shares them (see {@link RateLimitBuckets}). When Redis cannot be reached the request goes through: a
 * short outage of the limiter must not take the whole API down with it.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String WIDGET_PREFIX = "/api/v1/widget/";

    private final List<CompiledRule> rules;
    private final RateLimitBuckets buckets;
    private final HandlerExceptionResolver exceptionResolver;
    private final Map<String, Counter> rejections;
    private final Counter unavailable;

    RateLimitFilter(RateLimitProperties properties, RateLimitBuckets buckets,
            HandlerExceptionResolver exceptionResolver, MeterRegistry meterRegistry) {
        this.rules = properties.rules().stream().map(CompiledRule::new).toList();
        this.buckets = buckets;
        this.exceptionResolver = exceptionResolver;
        this.unavailable = Counter.builder("supportchat.rate.limit.unavailable")
                .description("Requests let through because the rate limit store could not be reached")
                .register(meterRegistry);
        this.rejections = properties.rules().stream().collect(Collectors.toMap(RateLimitProperties.Rule::name,
                rule -> Counter.builder("supportchat.rate.limit.rejections")
                        .description("Requests rejected with 429 by the rate limiter")
                        .tag("rule", rule.name())
                        .register(meterRegistry),
                (first, second) -> first));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        CompiledRule rule = findRule(request.getMethod(), path);
        // CORS preflights carry no credentials and cost nothing; counting them would halve every widget limit
        if (rule == null || "OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        ConsumptionProbe probe;
        try {
            probe = buckets.bucket(rule.name() + "|" + clientKey(rule, request, path), rule.capacity(), rule.period())
                    .tryConsumeAndReturnRemaining(1);
        } catch (RuntimeException e) {
            unavailable.increment();
            log.warn("Rate limit store unavailable, letting the request through: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }
        if (probe.isConsumed()) {
            response.setHeader(REMAINING_HEADER, String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        rejections.get(rule.name()).increment();
        response.setHeader(REMAINING_HEADER, "0");
        exceptionResolver.resolveException(request, response, null,
                new RateLimitExceededException(Duration.ofNanos(probe.getNanosToWaitForRefill())));
    }

    private CompiledRule findRule(String method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        for (CompiledRule rule : rules) {
            if (rule.matches(method, container)) {
                return rule;
            }
        }
        return null;
    }

    private static String clientKey(CompiledRule rule, HttpServletRequest request, String path) {
        switch (rule.key()) {
            case USER -> {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken)) {
                    return "user:" + authentication.getName();
                }
            }
            case WIDGET -> {
                if (path.startsWith(WIDGET_PREFIX)) {
                    int end = path.indexOf('/', WIDGET_PREFIX.length());
                    return "widget:" + path.substring(WIDGET_PREFIX.length(), end < 0 ? path.length() : end);
                }
            }
            case IP -> {
                // Falls through to the address below
            }
        }
        // Behind a proxy this is only the client's address when server.forward-headers-strategy is configured;
        // X-Forwarded-For is never read directly because clients can forge it
        return "ip:" + request.getRemoteAddr();
    }

    private record CompiledRule(String name, List<String> methods, List<PathPattern> paths,
            RateLimitProperties.ClientKey key, long capacity, Duration period) {

        CompiledRule(RateLimitProperties.Rule rule) {
            this(rule.name(), rule.methods().stream().map(String::toUpperCase).toList(),
                    rule.paths().stream().map(PathPatternParser.defaultInstance::parse).toList(),
                    rule.key(), rule.capacity(), rule.period());
        }

        boolean matches(String method, PathContainer path) {
            return (methods.isEmpty() || methods.contains(method))
                    && paths.stream().anyMatch(pattern -> pattern.matches(path));
        }
    }
}
