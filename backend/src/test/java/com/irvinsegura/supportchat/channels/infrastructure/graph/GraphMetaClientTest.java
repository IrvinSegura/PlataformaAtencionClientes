package com.irvinsegura.supportchat.channels.infrastructure.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.channels.domain.model.MetaChannel;
import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage.Button;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GraphMetaClientTest {

    @Test
    void messengerCountsCharacters() {
        String text = ("ñandú ".repeat(400)).strip();

        List<String> parts = GraphMetaClient.split(text, MetaPlatform.MESSENGER);

        assertThat(parts).hasSize(2).allSatisfy(part -> assertThat(part.length()).isLessThanOrEqualTo(2000));
        assertThat(String.join(" ", parts)).isEqualTo(text);
    }

    @Test
    void instagramCountsUtf8Bytes() {
        // 800 characters but 1600 bytes
        String text = "ñ".repeat(800);

        List<String> parts = GraphMetaClient.split(text, MetaPlatform.INSTAGRAM);

        assertThat(parts).hasSize(2).allSatisfy(part ->
                assertThat(part.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(1000));
        assertThat(String.join("", parts)).isEqualTo(text);
        assertThat(GraphMetaClient.split(text, MetaPlatform.MESSENGER)).containsExactly(text);
    }

    @Test
    void textsAreCutAtLineBreaksAndNeverInsideACharacter() {
        String first = "a".repeat(900);
        String second = "b".repeat(900);

        assertThat(GraphMetaClient.split(first + "\n" + second, MetaPlatform.INSTAGRAM))
                .containsExactly(first, second);
        // Emoji take two UTF-16 units and four bytes
        String emoji = "😀".repeat(300);
        List<String> parts = GraphMetaClient.split(emoji, MetaPlatform.INSTAGRAM);
        assertThat(parts).hasSize(2);
        assertThat(String.join("", parts)).isEqualTo(emoji);
        assertThat(parts.getFirst().codePoints()).allMatch(codePoint -> codePoint == "😀".codePointAt(0));
        assertThat(GraphMetaClient.split("  ", MetaPlatform.MESSENGER)).containsExactly("");
    }

    @Test
    void buttonsBecomeQuickReplies() {
        Map<String, Object> body = GraphMetaClient.body("2001", "¿Confirmas?",
                List.of(new Button("confirm:1", "Confirmar"), new Button("cancel:1", "Cancelar")));

        assertThat(body).containsEntry("recipient", Map.of("id", "2001"))
                .containsEntry("messaging_type", "RESPONSE");
        assertThat(body.get("message")).isEqualTo(Map.of("text", "¿Confirmas?", "quick_replies", List.of(
                Map.of("content_type", "text", "title", "Confirmar", "payload", "confirm:1"),
                Map.of("content_type", "text", "title", "Cancelar", "payload", "cancel:1"))));
        assertThat(GraphMetaClient.body("2001", "Hola", List.of()).get("message")).isEqualTo(Map.of("text", "Hola"));
    }

    @Test
    void aLongReplyArrivesWholeAndOnlyItsLastPieceCarriesTheQuickReplies() throws Exception {
        String text = "palabra ".repeat(600).strip();
        List<JsonNode> sent = new CopyOnWriteArrayList<>();
        JsonMapper json = JsonMapper.builder().build();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/me/messages", exchange -> {
            sent.add(json.readTree(exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try (HttpClient http = HttpClient.newHttpClient()) {
            GraphMetaClient client = new GraphMetaClient(http, json,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/", Duration.ofSeconds(5));
            MetaChannel channel = MetaChannel.restore(UUID.randomUUID(), MetaPlatform.MESSENGER, "key", "verify",
                    "1000001", null, "token", "secret", true, Instant.EPOCH, Instant.EPOCH);

            // Longer than WhatsApp's 1024 characters for a message with buttons: Messenger gets all of it
            client.send(channel, new OutboundMessage("2001", text, List.of(new Button("confirm:1", "Confirmar"))));
        } finally {
            server.stop(0);
        }

        assertThat(sent).hasSize(3);
        assertThat(sent.stream().map(body -> body.path("message").path("text").asString()).toList())
                .satisfies(parts -> assertThat(String.join(" ", parts)).isEqualTo(text));
        assertThat(sent.subList(0, 2)).noneMatch(body -> body.path("message").has("quick_replies"));
        assertThat(sent.getLast().path("message").path("quick_replies").get(0).path("payload").asString())
                .isEqualTo("confirm:1");
    }
}
