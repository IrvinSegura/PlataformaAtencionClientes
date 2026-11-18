package com.irvinsegura.supportchat.actions.infrastructure.webhook;

import com.irvinsegura.supportchat.actions.domain.model.EndpointNotAllowedException;
import com.irvinsegura.supportchat.actions.domain.model.WebhookCall;
import com.irvinsegura.supportchat.actions.domain.model.WebhookResult;
import com.irvinsegura.supportchat.actions.domain.port.ActionWebhook;
import com.irvinsegura.supportchat.actions.domain.port.EndpointPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Posts a JSON envelope to the business's webhook, signed like Stripe's webhooks so the business can tell it comes
 * from the platform and is fresh: {@code X-SupportChat-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256 of
 * "<t>.<body>">}. Redirects are not followed (they could lead into a private network the policy never saw), every
 * timeout is explicit and only the start of the answer is read.
 */
final class HttpActionWebhook implements ActionWebhook {

    static final String SIGNATURE_HEADER = "X-SupportChat-Signature";
    static final String DELIVERY_HEADER = "X-SupportChat-Delivery";
    static final String ACTION_HEADER = "X-SupportChat-Action";

    private static final Logger log = LoggerFactory.getLogger(HttpActionWebhook.class);
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final HttpClient http;
    private final JsonMapper json;
    private final EndpointPolicy policy;
    private final Duration requestTimeout;
    private final int maxResponseBytes;

    HttpActionWebhook(HttpClient http, JsonMapper json, EndpointPolicy policy, Duration requestTimeout,
            int maxResponseBytes) {
        this.http = http;
        this.json = json;
        this.policy = policy;
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public WebhookResult call(WebhookCall call) {
        long start = System.nanoTime();
        try {
            policy.check(call.url());
        } catch (EndpointNotAllowedException | IllegalArgumentException e) {
            return WebhookResult.unreachable("Blocked: " + e.getMessage(), 0);
        }
        String body = json.writeValueAsString(envelope(call));
        long timestamp = call.sentAt().getEpochSecond();
        HttpRequest request = HttpRequest.newBuilder(URI.create(call.url()))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", "SupportChat-Actions/1")
                .header(SIGNATURE_HEADER, "t=" + timestamp + ",v1=" + sign(call.signingSecret(), timestamp, body))
                .header(DELIVERY_HEADER, call.runId().toString())
                .header(ACTION_HEADER, call.actionName())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String text;
            try (InputStream in = response.body()) {
                text = new String(in.readNBytes(maxResponseBytes), StandardCharsets.UTF_8);
            }
            int status = response.statusCode();
            boolean ok = status >= 200 && status < 300;
            return new WebhookResult(status, text, ok ? null : "The webhook answered " + status,
                    ok ? visitorMessage(text) : null, elapsed(start));
        } catch (HttpTimeoutException e) {
            return WebhookResult.unreachable("The webhook did not answer within " + requestTimeout, elapsed(start));
        } catch (IOException e) {
            log.info("Action webhook {} could not be reached: {}", call.actionName(), e.toString());
            return WebhookResult.unreachable("The webhook could not be reached", elapsed(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WebhookResult.unreachable("Interrupted while calling the webhook", elapsed(start));
        }
    }

    private static Map<String, Object> envelope(WebhookCall call) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("action", call.actionName());
        envelope.put("runId", call.runId());
        envelope.put("workspaceId", call.workspaceId());
        envelope.put("conversationId", call.conversationId());
        envelope.put("channel", call.channel());
        // Verified by the channel (the WhatsApp number), never written by the model; null for web visitors
        envelope.put("customerId", call.contactId());
        envelope.put("arguments", call.arguments());
        envelope.put("sentAt", call.sentAt().toString());
        return envelope;
    }

    static String sign(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is required on every Java platform", e);
        }
    }

    // A JSON answer may carry {"message": "..."} for the customer, e.g. "Tu dirección quedó actualizada"
    private String visitorMessage(String text) {
        try {
            JsonNode node = json.readTree(text);
            JsonNode message = node == null ? null : node.get("message");
            if (message != null && message.isString()) {
                String value = message.asString().strip();
                return value.isEmpty() || value.length() > MAX_MESSAGE_LENGTH ? null : value;
            }
        } catch (JacksonException e) {
            // Not JSON: the answer is still given to the model as text
        }
        return null;
    }

    private static long elapsed(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }
}
