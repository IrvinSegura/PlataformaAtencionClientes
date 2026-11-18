package com.irvinsegura.supportchat.conversations.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.conversations.domain.model.ChatModelUnavailableException;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ChatTurn;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolCall;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolField;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolResult;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolSpec;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel.Completion;
import com.irvinsegura.supportchat.conversations.infrastructure.llm.ChatModels.TierModel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to a local HTTP server that speaks the Messages streaming protocol, so the adapter's parsing and error
 * handling are tested without the real API.
 */
class AnthropicChatModelTest {

    private static final ChatPrompt PROMPT = new ChatPrompt("Be helpful", List.of(
            new ChatTurn(ChatTurn.Role.VISITOR, "Hi"),
            new ChatTurn(ChatTurn.Role.ASSISTANT, "Hello!"),
            new ChatTurn(ChatTurn.Role.VISITOR, "When do you open?")), List.of());

    private static final ToolSpec ORDER_STATUS = new ToolSpec("order_status", "Looks up an order",
            List.of(new ToolField("order_number", "string", "The order number", true, "\\d{5,10}"),
                    new ToolField("email", "string", "The buyer's email", false, null)));

    private static final ChatPrompt TOOL_PROMPT = new ChatPrompt("Be helpful", List.of(
            new ChatTurn(ChatTurn.Role.VISITOR, "Where is order 12345?")), List.of(), List.of(ORDER_STATUS));

    private final AtomicReference<Responder> responder = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            responder.get().respond(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void streamsTheTextAndReportsUsage() {
        responder.set(exchange -> stream(exchange, false,
                event("message_start", "{\"type\":\"message_start\",\"message\":{\"model\":\"claude-test-1\","
                        + "\"usage\":{\"input_tokens\":42,\"output_tokens\":1}}}"),
                event("ping", "{\"type\":\"ping\"}"),
                event("content_block_delta", delta("We open ")),
                event("content_block_delta", delta("at 9 [1].")),
                event("message_delta", "{\"type\":\"message_delta\",\"usage\":{\"output_tokens\":7}}"),
                event("message_stop", "{\"type\":\"message_stop\"}")));
        List<String> pieces = new ArrayList<>();

        Completion completion = model(Duration.ofSeconds(5)).stream(PROMPT, ModelTier.SIMPLE, pieces::add);

        assertThat(pieces).containsExactly("We open ", "at 9 [1].");
        assertThat(completion).isEqualTo(new Completion("claude-test-1", 42, 7));
        assertThat(apiKeyHeader.get()).isEqualTo("test-key");
        JsonNode body = JsonMapper.builder().build().readTree(requestBody.get());
        assertThat(body.path("model").asString()).isEqualTo("claude-simple");
        assertThat(body.path("output_config").path("effort").asString()).isEqualTo("low");
        assertThat(body.has("tools")).isFalse();
        assertThat(body.path("system").asString()).isEqualTo("Be helpful");
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("max_tokens").asInt()).isEqualTo(256);
        assertThat(body.path("messages")).extracting(message -> message.path("role").asString())
                .containsExactly("user", "assistant", "user");
        assertThat(body.path("messages").get(1).path("content").asString()).isEqualTo("Hello!");
    }

    @Test
    void theComplexTierUsesTheCapableModel() {
        responder.set(exchange -> stream(exchange, false, finished("end_turn")));

        model(Duration.ofSeconds(5)).stream(PROMPT, ModelTier.COMPLEX, text -> {
        });

        JsonNode body = JsonMapper.builder().build().readTree(requestBody.get());
        assertThat(body.path("model").asString()).isEqualTo("claude-complex");
        assertThat(body.path("output_config").path("effort").asString()).isEqualTo("medium");
    }

    @Test
    void offersToolsAndReturnsTheCallsWithTheTurnToReplay() {
        responder.set(exchange -> stream(exchange, false,
                event("message_start", "{\"type\":\"message_start\",\"message\":{\"model\":\"claude-simple\","
                        + "\"usage\":{\"input_tokens\":50,\"output_tokens\":1}}}"),
                event("content_block_start", "{\"type\":\"content_block_start\",\"index\":0,"
                        + "\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}"),
                event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"Need the order.\"}}"),
                event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"signature_delta\",\"signature\":\"sig-1\"}}"),
                event("content_block_start", "{\"type\":\"content_block_start\",\"index\":1,"
                        + "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"),
                event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":1,"
                        + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Let me check.\"}}"),
                event("content_block_start", "{\"type\":\"content_block_start\",\"index\":2,"
                        + "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"order_status\","
                        + "\"input\":{}}}"),
                event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":2,"
                        + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"order_number\\\": \"}}"),
                event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":2,"
                        + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"12345\\\"}\"}}"),
                finished("tool_use")));
        List<String> pieces = new ArrayList<>();

        Completion completion = model(Duration.ofSeconds(5)).stream(TOOL_PROMPT, ModelTier.SIMPLE, pieces::add);

        assertThat(pieces).containsExactly("Let me check.");
        assertThat(completion.toolCalls())
                .containsExactly(new ToolCall("toolu_1", "order_status", Map.of("order_number", "12345")));
        JsonMapper json = JsonMapper.builder().build();
        JsonNode tool = json.readTree(requestBody.get()).path("tools").get(0);
        assertThat(tool.path("name").asString()).isEqualTo("order_status");
        assertThat(tool.path("input_schema").path("required")).extracting(JsonNode::asString)
                .containsExactly("order_number");
        assertThat(tool.path("input_schema").path("properties").path("order_number").path("pattern").asString())
                .isEqualTo("\\d{5,10}");
        JsonNode replay = json.readTree(completion.replay());
        assertThat(replay).extracting(block -> block.path("type").asString())
                .containsExactly("thinking", "text", "tool_use");
        assertThat(replay.get(0).path("signature").asString()).isEqualTo("sig-1");
        assertThat(replay.get(2).path("input").path("order_number").asString()).isEqualTo("12345");
    }

    @Test
    void sendsTheReplayedTurnAndTheToolResultsBack() {
        responder.set(exchange -> stream(exchange, false, finished("end_turn")));
        String replay = "[{\"type\":\"thinking\",\"thinking\":\"t\",\"signature\":\"s\"},"
                + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"order_status\","
                + "\"input\":{\"order_number\":\"12345\"}}]";
        ChatPrompt followUp = new ChatPrompt("Be helpful", List.of(
                new ChatTurn(ChatTurn.Role.VISITOR, "Where is order 12345?"),
                new ChatTurn(ChatTurn.Role.ASSISTANT, "", List.of(new ToolCall("toolu_1", "order_status",
                        Map.of("order_number", "12345"))), List.of(), replay),
                new ChatTurn(ChatTurn.Role.VISITOR, "", List.of(),
                        List.of(new ToolResult("toolu_1", "Shipped yesterday", false)), null)),
                List.of(), List.of(ORDER_STATUS));

        model(Duration.ofSeconds(5)).stream(followUp, ModelTier.SIMPLE, text -> {
        });

        JsonNode messages = JsonMapper.builder().build().readTree(requestBody.get()).path("messages");
        assertThat(messages.get(1).path("content").get(0).path("signature").asString()).isEqualTo("s");
        JsonNode result = messages.get(2).path("content").get(0);
        assertThat(result.path("type").asString()).isEqualTo("tool_result");
        assertThat(result.path("tool_use_id").asString()).isEqualTo("toolu_1");
        assertThat(result.path("content").asString()).isEqualTo("Shipped yesterday");
        assertThat(result.has("is_error")).isFalse();
    }

    @Test
    void toolCallsCutOffByTheTokenLimitAreNotRun() {
        responder.set(exchange -> stream(exchange, false,
                event("content_block_start", "{\"type\":\"content_block_start\",\"index\":0,"
                        + "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"order_status\"}}"),
                event("content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"order_num\"}}"),
                finished("max_tokens")));

        Completion completion = model(Duration.ofSeconds(5)).stream(TOOL_PROMPT, ModelTier.SIMPLE, text -> {
        });

        assertThat(completion.toolCalls()).isEmpty();
    }

    @Test
    void aRefusalIsNotAnAnswer() {
        responder.set(exchange -> stream(exchange, false, finished("refusal")));

        assertThatThrownBy(() -> model(Duration.ofSeconds(5)).stream(PROMPT, ModelTier.SIMPLE, text -> {
        })).isInstanceOf(ChatModelUnavailableException.class);
    }

    @Test
    void overloadAndRateLimitsAreTemporaryFailures() {
        for (int status : new int[] {429, 500, 529}) {
            responder.set(exchange -> plain(exchange, status, "{\"type\":\"error\"}"));

            assertThatThrownBy(() -> model(Duration.ofSeconds(5)).stream(PROMPT, ModelTier.SIMPLE, text -> {
            })).isInstanceOf(ChatModelUnavailableException.class);
        }
    }

    @Test
    void aRejectedRequestIsAConfigurationError() {
        responder.set(exchange -> plain(exchange, 401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"}}"));

        assertThatThrownBy(() -> model(Duration.ofSeconds(5)).stream(PROMPT, ModelTier.SIMPLE, text -> {
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("401");
    }

    @Test
    void anErrorEventInsideTheStreamIsATemporaryFailure() {
        responder.set(exchange -> stream(exchange, false,
                event("content_block_delta", delta("Partial")),
                event("error", "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}")));

        assertThatThrownBy(() -> model(Duration.ofSeconds(5)).stream(PROMPT, ModelTier.SIMPLE, text -> {
        })).isInstanceOf(ChatModelUnavailableException.class).hasMessageContaining("overloaded_error");
    }

    @Test
    void aStreamThatStopsSendingIsAbandoned() {
        responder.set(exchange -> stream(exchange, true, event("content_block_delta", delta("Thinking..."))));

        long start = System.nanoTime();
        assertThatThrownBy(() -> model(Duration.ofMillis(1500)).stream(PROMPT, ModelTier.SIMPLE, text -> {
        })).isInstanceOf(ChatModelUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void aMissingApiKeyFailsAtStartup() {
        assertThatThrownBy(() -> new AnthropicChatModel(HttpClient.newHttpClient(), JsonMapper.builder().build(),
                "http://localhost", " ", TIERS, 256, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    private AnthropicChatModel model(Duration readTimeout) {
        return new AnthropicChatModel(HttpClient.newHttpClient(), JsonMapper.builder().build(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-key", TIERS, 256, readTimeout);
    }

    private static final Map<ModelTier, TierModel> TIERS = Map.of(
            ModelTier.SIMPLE, new TierModel("claude-simple", "low"),
            ModelTier.COMPLEX, new TierModel("claude-complex", "medium"));

    private static String finished(String stopReason) {
        return event("message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + stopReason
                + "\"},\"usage\":{\"output_tokens\":3}}") + event("message_stop", "{\"type\":\"message_stop\"}");
    }

    private static String event(String name, String data) {
        return "event: " + name + "\ndata: " + data + "\n\n";
    }

    private static String delta(String text) {
        return "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"" + text
                + "\"}}";
    }

    private static void stream(HttpExchange exchange, boolean hang, String... events) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        OutputStream body = exchange.getResponseBody();
        for (String event : events) {
            body.write(event.getBytes(StandardCharsets.UTF_8));
            body.flush();
        }
        if (hang) {
            try {
                Thread.sleep(Duration.ofSeconds(8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        exchange.close();
    }

    private static void plain(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface Responder {

        void respond(HttpExchange exchange) throws IOException;
    }
}
