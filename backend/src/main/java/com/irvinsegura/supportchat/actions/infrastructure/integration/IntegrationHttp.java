package com.irvinsegura.supportchat.actions.infrastructure.integration;

import com.irvinsegura.supportchat.actions.domain.model.WebhookResult;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Posts JSON to a provider's API: explicit timeouts, no redirects (set on the client) and only the start of the answer
 * read, as with the businesses' webhooks.
 */
final class IntegrationHttp {

    // The status recorded when a provider answered but not as documented, or refused what was asked
    static final int BAD_GATEWAY = 502;

    private static final Logger log = LoggerFactory.getLogger(IntegrationHttp.class);

    private final HttpClient http;
    private final JsonMapper json;
    private final Duration requestTimeout;
    private final int maxResponseBytes;

    IntegrationHttp(HttpClient http, JsonMapper json, Duration requestTimeout, int maxResponseBytes) {
        this.http = http;
        this.json = json;
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
    }

    /**
     * @return the answer, or a failure when the provider could not be reached or did not answer JSON
     */
    Answer post(String provider, String url, Map<String, String> headers, Object body) {
        long start = System.nanoTime();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "SupportChat-Integrations/1")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
        headers.forEach(request::header);
        try {
            HttpResponse<InputStream> response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            String text;
            try (InputStream in = response.body()) {
                text = new String(in.readNBytes(maxResponseBytes), StandardCharsets.UTF_8);
            }
            JsonNode parsed;
            try {
                parsed = text.isBlank() ? null : json.readTree(text);
            } catch (JacksonException e) {
                parsed = null;
            }
            return new Answer(response.statusCode(), text, parsed, elapsed(start));
        } catch (HttpTimeoutException e) {
            return Answer.failed(WebhookResult.unreachable(provider + " did not answer within " + requestTimeout,
                    elapsed(start)));
        } catch (IOException e) {
            log.info("{} could not be reached: {}", provider, e.toString());
            return Answer.failed(WebhookResult.unreachable(provider + " could not be reached", elapsed(start)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Answer.failed(WebhookResult.unreachable("Interrupted while calling " + provider, elapsed(start)));
        }
    }

    String write(Object value) {
        return json.writeValueAsString(value);
    }

    static long elapsed(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    /**
     * @param body    the JSON answer, or null when it was not JSON
     * @param failure set when nothing came back; the other fields are then empty
     */
    record Answer(int status, String text, JsonNode body, long latencyMillis, WebhookResult failure) {

        Answer(int status, String text, JsonNode body, long latencyMillis) {
            this(status, text, body, latencyMillis, null);
        }

        static Answer failed(WebhookResult failure) {
            return new Answer(0, null, null, failure.latencyMillis(), failure);
        }

        boolean ok() {
            return failure == null && status / 100 == 2 && body != null;
        }

        /**
         * The provider's own answer when it refused the call, e.g. a revoked token.
         */
        WebhookResult refused(String provider) {
            if (failure != null) {
                return failure;
            }
            if (status / 100 == 2) {
                // Answered, but not with what the API documents: reported as a bad gateway so the run fails
                return new WebhookResult(BAD_GATEWAY, text, provider + " did not answer JSON", null, latencyMillis);
            }
            String reason = status == 401 || status == 403
                    ? provider + " refused the token; reconnect the integration"
                    : provider + " answered " + status;
            return new WebhookResult(status, text, reason, null, latencyMillis);
        }
    }
}
