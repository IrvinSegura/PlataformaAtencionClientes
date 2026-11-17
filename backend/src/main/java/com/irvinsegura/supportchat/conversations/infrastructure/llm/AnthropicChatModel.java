package com.irvinsegura.supportchat.conversations.infrastructure.llm;

import com.irvinsegura.supportchat.conversations.domain.model.ChatModelUnavailableException;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ChatTurn;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolCall;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolField;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolResult;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolSpec;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel;
import com.irvinsegura.supportchat.conversations.infrastructure.llm.ChatModels.TierModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude through the Messages API with streaming (https://docs.claude.com/en/api/messages-streaming). Written on the
 * JDK HTTP client, so the adapter has no SDK to keep in step and every timeout is explicit.
 * <p>
 * Each tier maps to one model and effort, e.g. Haiku thinking little for direct questions and Sonnet thinking more
 * for the rest. Actions are offered as client tools; when Claude asks for one, the turn's content blocks (thinking
 * included, with its signature) are returned as the replay, because the API needs them back unchanged with the tool
 * results.
 */
class AnthropicChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(AnthropicChatModel.class);
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_ERROR_BODY_CHARS = 500;
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT = new TypeReference<>() {
    };

    // Closes streams that stopped sending; one daemon thread serves every answer
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "anthropic-stream-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    private final HttpClient http;
    private final JsonMapper json;
    private final URI endpoint;
    private final String apiKey;
    private final Map<ModelTier, TierModel> models;
    private final int maxOutputTokens;
    private final Duration readTimeout;

    AnthropicChatModel(HttpClient http, JsonMapper json, String baseUrl, String apiKey,
            Map<ModelTier, TierModel> models, int maxOutputTokens, Duration readTimeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is required when the chat model provider is anthropic");
        }
        for (ModelTier tier : ModelTier.values()) {
            if (!models.containsKey(tier)) {
                throw new IllegalArgumentException("No Claude model configured for the " + tier + " tier");
            }
        }
        this.http = http;
        this.json = json;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/messages");
        this.apiKey = apiKey;
        this.models = new EnumMap<>(models);
        this.maxOutputTokens = maxOutputTokens;
        this.readTimeout = readTimeout;
    }

    @Override
    public String modelName(ModelTier tier) {
        return models.get(tier).model();
    }

    @Override
    public Completion stream(ChatPrompt prompt, ModelTier tier, Consumer<String> onText) {
        TierModel model = models.get(tier);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                // Bounds the wait for the response headers; the body is watched separately below
                .timeout(readTimeout)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt, model), StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = send(request);
        try (InputStream body = response.body()) {
            int status = response.statusCode();
            if (status != 200) {
                throw failure(status, body);
            }
            return readEvents(body, model.model(), onText);
        } catch (IOException e) {
            throw new ChatModelUnavailableException("The answer stream from Claude broke", e);
        }
    }

    private HttpResponse<InputStream> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new ChatModelUnavailableException("Claude could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatModelUnavailableException("Interrupted while calling Claude", e);
        }
    }

    String requestBody(ChatPrompt prompt, TierModel model) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatTurn turn : prompt.turns()) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", turn.role() == ChatTurn.Role.VISITOR ? "user" : "assistant");
            message.put("content", content(turn));
            messages.add(message);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.model());
        body.put("max_tokens", maxOutputTokens);
        body.put("system", prompt.system());
        body.put("messages", messages);
        if (!prompt.tools().isEmpty()) {
            body.put("tools", prompt.tools().stream().map(AnthropicChatModel::tool).toList());
        }
        body.put("output_config", Map.of("effort", model.effort()));
        body.put("stream", true);
        return json.writeValueAsString(body);
    }

    private Object content(ChatTurn turn) {
        if (turn.role() == ChatTurn.Role.ASSISTANT && turn.replay() != null) {
            // Thinking blocks must go back exactly as Claude wrote them, signature included
            return parse(turn.replay());
        }
        if (turn.toolCalls().isEmpty() && turn.toolResults().isEmpty()) {
            return turn.text();
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        // Tool results must come first in a user turn
        for (ToolResult result : turn.toolResults()) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "tool_result");
            block.put("tool_use_id", result.toolCallId());
            block.put("content", result.content());
            if (result.error()) {
                block.put("is_error", true);
            }
            blocks.add(block);
        }
        if (!turn.text().isBlank()) {
            blocks.add(Map.of("type", "text", "text", turn.text()));
        }
        for (ToolCall call : turn.toolCalls()) {
            blocks.add(Map.of("type", "tool_use", "id", call.id(), "name", call.name(), "input", call.input()));
        }
        return blocks;
    }

    private static Map<String, Object> tool(ToolSpec spec) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolField field : spec.fields()) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", field.type());
            property.put("description", field.description());
            if (field.pattern() != null) {
                property.put("pattern", field.pattern());
            }
            properties.put(field.name(), property);
            if (field.required()) {
                required.add(field.name());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return Map.of("name", spec.name(), "description", spec.description(), "input_schema", schema);
    }

    private Completion readEvents(InputStream body, String model, Consumer<String> onText) throws IOException {
        AtomicLong lastRead = new AtomicLong(System.nanoTime());
        AtomicBoolean stalled = new AtomicBoolean();
        ScheduledFuture<?> watchdog = WATCHDOG.scheduleAtFixedRate(() -> {
            if (System.nanoTime() - lastRead.get() > readTimeout.toNanos()) {
                stalled.set(true);
                closeQuietly(body);
            }
        }, 1, 1, TimeUnit.SECONDS);
        String answeredBy = model;
        int inputTokens = 0;
        int outputTokens = 0;
        String stopReason = null;
        Map<Integer, Block> blocks = new TreeMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lastRead.set(System.nanoTime());
                if (!line.startsWith("data:")) {
                    // Event names, keep-alive comments and blank separators carry nothing the data lines lack
                    continue;
                }
                JsonNode event = parse(line.substring(5).strip());
                switch (event.path("type").asString("")) {
                    case "message_start" -> {
                        JsonNode message = event.path("message");
                        answeredBy = message.path("model").asString(model);
                        inputTokens = message.path("usage").path("input_tokens").asInt(0);
                        outputTokens = message.path("usage").path("output_tokens").asInt(0);
                    }
                    case "content_block_start" ->
                            blocks.put(event.path("index").asInt(), Block.start(event.path("content_block")));
                    case "content_block_delta" -> {
                        JsonNode delta = event.path("delta");
                        Block block = blocks.get(event.path("index").asInt());
                        String type = delta.path("type").asString("");
                        if ("text_delta".equals(type)) {
                            String text = delta.path("text").asString("");
                            if (block != null) {
                                block.text.append(text);
                            }
                            onText.accept(text);
                        } else if (block != null) {
                            block.accept(type, delta);
                        }
                    }
                    case "message_delta" -> {
                        stopReason = event.path("delta").path("stop_reason").asString(stopReason);
                        // Output tokens in message_delta are cumulative for the whole message
                        outputTokens = event.path("usage").path("output_tokens").asInt(outputTokens);
                    }
                    case "message_stop" -> {
                        return completion(answeredBy, inputTokens, outputTokens, stopReason, blocks.values());
                    }
                    case "error" -> throw new ChatModelUnavailableException(
                            "Claude stopped with " + event.path("error").path("type").asString("an error"), null);
                    default -> {
                        // ping, content_block_stop and future event types
                    }
                }
            }
        } catch (IOException e) {
            if (stalled.get()) {
                throw new ChatModelUnavailableException("Claude sent nothing for " + readTimeout, e);
            }
            throw e;
        } finally {
            watchdog.cancel(false);
        }
        throw new ChatModelUnavailableException("Claude's answer stream ended before message_stop", null);
    }

    private Completion completion(String model, int inputTokens, int outputTokens, String stopReason,
            Iterable<Block> blocks) {
        if ("refusal".equals(stopReason)) {
            throw new ChatModelUnavailableException("Claude declined to answer", null);
        }
        if (!"tool_use".equals(stopReason)) {
            // A complete answer; tool calls cut off by max_tokens are not run with half-written inputs
            return new Completion(model, inputTokens, outputTokens);
        }
        List<ToolCall> calls = new ArrayList<>();
        List<Map<String, Object>> replay = new ArrayList<>();
        for (Block block : blocks) {
            Map<String, Object> encoded = block.encode(this);
            if (encoded == null) {
                continue;
            }
            replay.add(encoded);
            if ("tool_use".equals(block.type)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> input = (Map<String, Object>) encoded.get("input");
                calls.add(new ToolCall(block.id, block.name, input));
            }
        }
        return new Completion(model, inputTokens, outputTokens, calls, json.writeValueAsString(replay));
    }

    private Map<String, Object> toolInput(String partialJson) {
        if (partialJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return json.readValue(partialJson, OBJECT);
        } catch (JacksonException e) {
            // Validation then rejects the call and the model is told why, instead of the answer failing
            log.warn("Claude wrote tool input that is not a JSON object");
            return new LinkedHashMap<>();
        }
    }

    private JsonNode parse(String data) {
        try {
            return json.readTree(data);
        } catch (JacksonException e) {
            throw new ChatModelUnavailableException("Claude sent an unreadable event", e);
        }
    }

    private RuntimeException failure(int status, InputStream body) throws IOException {
        String detail = new String(body.readNBytes(MAX_ERROR_BODY_CHARS), StandardCharsets.UTF_8);
        // 429 rate limited, 529 overloaded, 5xx server errors: worth retrying later
        if (status == 429 || status >= 500) {
            return new ChatModelUnavailableException("Claude answered " + status, null);
        }
        // Anything else (bad key, bad request, unknown model) is a configuration problem for whoever runs this
        log.error("Claude rejected the request with status {}: {}", status, detail);
        return new IllegalStateException("Claude rejected the request with status " + status);
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Closing only to interrupt a blocked read
        }
    }

    /**
     * One content block as it streams in.
     */
    private static final class Block {

        private final String type;
        private final String id;
        private final String name;
        private final String data;
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final StringBuilder signature = new StringBuilder();
        private final StringBuilder input = new StringBuilder();

        private Block(String type, String id, String name, String data) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.data = data;
        }

        static Block start(JsonNode block) {
            Block started = new Block(block.path("type").asString(""), block.path("id").asString(null),
                    block.path("name").asString(null), block.path("data").asString(null));
            started.thinking.append(block.path("thinking").asString(""));
            started.signature.append(block.path("signature").asString(""));
            started.text.append(block.path("text").asString(""));
            return started;
        }

        void accept(String deltaType, JsonNode delta) {
            switch (deltaType) {
                case "thinking_delta" -> thinking.append(delta.path("thinking").asString(""));
                case "signature_delta" -> signature.append(delta.path("signature").asString(""));
                case "input_json_delta" -> input.append(delta.path("partial_json").asString(""));
                default -> {
                    // Deltas of block types this adapter does not use
                }
            }
        }

        /**
         * @return the block as the API expects it back, or null for blocks not sent back
         */
        Map<String, Object> encode(AnthropicChatModel model) {
            Map<String, Object> encoded = new LinkedHashMap<>();
            encoded.put("type", type);
            switch (type) {
                case "thinking" -> {
                    encoded.put("thinking", thinking.toString());
                    encoded.put("signature", signature.toString());
                }
                case "redacted_thinking" -> encoded.put("data", data);
                case "text" -> {
                    if (text.isEmpty()) {
                        return null;
                    }
                    encoded.put("text", text.toString());
                }
                case "tool_use" -> {
                    encoded.put("id", id);
                    encoded.put("name", name);
                    encoded.put("input", model.toolInput(input.toString()));
                }
                default -> {
                    return null;
                }
            }
            return encoded;
        }
    }
}
