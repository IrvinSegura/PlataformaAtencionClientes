package com.irvinsegura.supportchat.channels.infrastructure.graph;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.irvinsegura.supportchat.channels.domain.model.DeliveryFailedException;
import com.irvinsegura.supportchat.channels.domain.model.MetaChannel;
import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.port.MetaGraph;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Messenger and Instagram through the Graph API with the Page access token: the Send API
 * (https://developers.facebook.com/docs/messenger-platform/reference/send-api) and the profiles of the people who
 * write. Buttons become quick replies, which both platforms show.
 */
public class GraphMetaClient implements MetaGraph {

    private static final Logger log = LoggerFactory.getLogger(GraphMetaClient.class);
    private static final int MAX_ERROR_CHARS = 500;
    private static final Duration NAME_TTL = Duration.ofHours(6);

    private final HttpClient http;
    private final JsonMapper json;
    private final String baseUrl;
    private final Duration requestTimeout;
    // Every message would otherwise look the name up again; empty when Meta did not give one
    private final Cache<String, Optional<String>> names = Caffeine.newBuilder()
            .expireAfterWrite(NAME_TTL)
            .maximumSize(10_000)
            .build();

    public GraphMetaClient(HttpClient http, JsonMapper json, String baseUrl, Duration requestTimeout) {
        this.http = http;
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.requestTimeout = requestTimeout;
    }

    @Override
    public void send(MetaChannel channel, OutboundMessage message) {
        List<String> parts = split(message.text(), channel.platform());
        for (int i = 0; i < parts.size(); i++) {
            boolean last = i == parts.size() - 1;
            post(channel, body(message.to(), parts.get(i), last ? message.buttons() : List.of()));
        }
    }

    private void post(MetaChannel channel, Map<String, Object> body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/me/messages"))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + channel.accessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = call(request);
        int status = response.statusCode();
        if (status / 100 == 2) {
            return;
        }
        if (status == 429 || status >= 500) {
            throw new DeliveryFailedException.Temporary(channel.platform() + " answered " + status, null);
        }
        String detail = response.body() == null ? ""
                : response.body().substring(0, Math.min(response.body().length(), MAX_ERROR_CHARS));
        log.warn("{} rejected a message with status {}: {}", channel.platform(), status, detail);
        throw new DeliveryFailedException.Permanent(channel.platform() + " answered " + status);
    }

    @Override
    public Optional<String> contactName(MetaChannel channel, String contactId) {
        String key = channel.workspaceId() + ":" + channel.platform() + ":" + contactId;
        return names.get(key, ignored -> lookUpName(channel, contactId));
    }

    private Optional<String> lookUpName(MetaChannel channel, String contactId) {
        String fields = channel.platform() == MetaPlatform.INSTAGRAM ? "name,username" : "name";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/"
                        + URLEncoder.encode(contactId, StandardCharsets.UTF_8) + "?fields=" + fields))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + channel.accessToken())
                .GET()
                .build();
        try {
            HttpResponse<String> response = call(request);
            if (response.statusCode() / 100 != 2) {
                return Optional.empty();
            }
            JsonNode profile = json.readTree(response.body());
            String name = profile.path("name").asString("").strip();
            String username = profile.path("username").asString("").strip();
            return Optional.of(name.isEmpty() ? (username.isEmpty() ? "" : "@" + username) : name)
                    .filter(found -> !found.isEmpty());
        } catch (DeliveryFailedException | JacksonException e) {
            // Only the operators see the name: the conversation goes on without it
            return Optional.empty();
        }
    }

    private HttpResponse<String> call(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DeliveryFailedException.Temporary("Meta could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeliveryFailedException.Temporary("Interrupted while calling Meta", e);
        }
    }

    static Map<String, Object> body(String recipient, String text, List<OutboundMessage.Button> buttons) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("text", text);
        if (!buttons.isEmpty()) {
            message.put("quick_replies", buttons.stream()
                    .map(button -> Map.of("content_type", "text", "title", button.title(), "payload", button.id()))
                    .toList());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipient", Map.of("id", recipient));
        // An answer to the customer's message, inside the 24 hours Meta allows
        body.put("messaging_type", "RESPONSE");
        body.put("message", message);
        return body;
    }

    /**
     * Cuts a text into pieces the platform accepts, at line breaks or spaces where possible.
     */
    static List<String> split(String text, MetaPlatform platform) {
        List<String> parts = new ArrayList<>();
        String rest = text.strip();
        while (!rest.isEmpty()) {
            int fits = fittingLength(rest, platform);
            if (fits == rest.length()) {
                parts.add(rest);
                break;
            }
            int cut = Math.max(rest.lastIndexOf('\n', fits), rest.lastIndexOf(' ', fits));
            // A cut too early would leave a stub; a word that long is cut where it must be
            int end = cut > fits / 2 ? cut : fits;
            parts.add(rest.substring(0, end).strip());
            rest = rest.substring(end).strip();
        }
        return parts.isEmpty() ? List.of("") : parts;
    }

    // How many chars from the start fit, never splitting a character in two
    private static int fittingLength(String text, MetaPlatform platform) {
        int size = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            size += platform.size(codePoint);
            if (size > platform.maxText()) {
                return index;
            }
            index += Character.charCount(codePoint);
        }
        return index;
    }
}
