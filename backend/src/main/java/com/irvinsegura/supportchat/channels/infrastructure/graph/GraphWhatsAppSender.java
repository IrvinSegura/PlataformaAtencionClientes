package com.irvinsegura.supportchat.channels.infrastructure.graph;

import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage.Button;
import com.irvinsegura.supportchat.channels.domain.model.TemplateMessage;
import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppSender;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends messages with the Cloud API's messages endpoint
 * (https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages).
 */
public class GraphWhatsAppSender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(GraphWhatsAppSender.class);
    private static final int MAX_ERROR_CHARS = 500;
    // Limits of the Cloud API: a text message, and the body of a message with reply buttons
    static final int MAX_TEXT = 4096;
    static final int MAX_BUTTON_TEXT = 1024;
    static final int MAX_TEMPLATE_VALUE = 1024;

    private final HttpClient http;
    private final JsonMapper json;
    private final String baseUrl;
    private final Duration requestTimeout;

    public GraphWhatsAppSender(HttpClient http, JsonMapper json, String baseUrl, Duration requestTimeout) {
        this.http = http;
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.requestTimeout = requestTimeout;
    }

    @Override
    public void send(WhatsAppChannel channel, OutboundMessage message) {
        post(channel, body(message));
    }

    @Override
    public void sendTemplate(WhatsAppChannel channel, TemplateMessage message) {
        post(channel, body(message));
    }

    private void post(WhatsAppChannel channel, Map<String, Object> body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + channel.phoneNumberId() + "/messages"))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + channel.accessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body),
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new WhatsAppUnavailableException("WhatsApp could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhatsAppUnavailableException("Interrupted while sending to WhatsApp", e);
        }
        int status = response.statusCode();
        if (status / 100 == 2) {
            return;
        }
        String detail = response.body() == null ? ""
                : response.body().substring(0, Math.min(response.body().length(), MAX_ERROR_CHARS));
        if (status == 429 || status >= 500) {
            throw new WhatsAppUnavailableException("WhatsApp answered " + status, null);
        }
        log.warn("WhatsApp rejected a message with status {}: {}", status, detail);
        throw new WhatsAppRejectedException("WhatsApp answered " + status);
    }

    static Map<String, Object> body(OutboundMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", recipient(message.to()));
        if (message.buttons().isEmpty()) {
            body.put("type", "text");
            body.put("text", Map.of("preview_url", false, "body", OutboundMessage.shorten(message.text(), MAX_TEXT)));
            return body;
        }
        List<Map<String, Object>> buttons = message.buttons().stream()
                .map(GraphWhatsAppSender::button)
                .toList();
        body.put("type", "interactive");
        body.put("interactive", Map.of(
                "type", "button",
                "body", Map.of("text", OutboundMessage.shorten(message.text(), MAX_BUTTON_TEXT)),
                "action", Map.of("buttons", buttons)));
        return body;
    }

    static Map<String, Object> body(TemplateMessage message) {
        List<Map<String, Object>> parameters = message.parameters().stream()
                .<Map<String, Object>>map(value -> Map.of("type", "text", "text", templateValue(value)))
                .toList();
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", message.name());
        template.put("language", Map.of("code", message.language()));
        if (!parameters.isEmpty()) {
            template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", recipient(message.to()));
        body.put("type", "template");
        body.put("template", template);
        return body;
    }

    /**
     * The number to send to. Webhooks report Mexican mobiles with the old "1" after 52 (521 and ten digits), but the
     * Cloud API delivers to them, and matches a test number's allowed recipients, only without it.
     */
    static String recipient(String to) {
        return to != null && to.matches("521\\d{10}") ? "52" + to.substring(3) : to;
    }

    /**
     * Meta refuses a variable with line breaks, tabs or more than four spaces in a row.
     */
    static String templateValue(String value) {
        String flat = value == null ? "" : value.replaceAll("[\r\n\t]+", " ").replaceAll(" {4,}", "   ").strip();
        return OutboundMessage.shorten(flat.isEmpty() ? "-" : flat, MAX_TEMPLATE_VALUE);
    }

    private static Map<String, Object> button(Button button) {
        return Map.of("type", "reply", "reply", Map.of("id", button.id(), "title", button.title()));
    }
}
