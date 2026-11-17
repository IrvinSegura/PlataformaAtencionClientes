package com.irvinsegura.supportchat.knowledge.infrastructure.embedding;

import com.irvinsegura.supportchat.knowledge.domain.model.Embedding;
import com.irvinsegura.supportchat.knowledge.domain.model.EmbeddingUnavailableException;
import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import com.irvinsegura.supportchat.knowledge.domain.port.EmbeddingModel;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Voyage AI embeddings (https://docs.voyageai.com/reference/embeddings-api). Passages and questions are embedded
 * with different {@code input_type}s, which Voyage tunes for retrieval.
 */
class VoyageEmbeddingModel implements EmbeddingModel {

    private final HttpClient http;
    private final JsonMapper json;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    VoyageEmbeddingModel(HttpClient http, JsonMapper json, String baseUrl, String apiKey, String model,
            Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("VOYAGE_API_KEY is required when the embedding provider is voyage");
        }
        this.http = http;
        this.json = json;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/embeddings");
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public List<Embedding> embedPassages(List<String> passages) {
        return passages.isEmpty() ? List.of() : embed(passages, "document");
    }

    @Override
    public Embedding embedQuery(String query) {
        return embed(List.of(query), "query").getFirst();
    }

    @Override
    public double relatedSimilarity() {
        return 0.35;
    }

    // Rephrasings of one question score above 0.8; questions on the same subject asked differently, around 0.7
    @Override
    public double sameTopicSimilarity() {
        return 0.7;
    }

    @Override
    public double sameQuestionSimilarity() {
        return 0.8;
    }

    private List<Embedding> embed(List<String> inputs, String inputType) {
        String body = json.writeValueAsString(Map.of(
                "input", inputs,
                "model", model,
                "input_type", inputType,
                "output_dimension", KnowledgeLimits.EMBEDDING_DIMENSIONS));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new EmbeddingUnavailableException("Voyage AI could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingUnavailableException("Interrupted while calling Voyage AI", e);
        }
        int status = response.statusCode();
        // Rate limits and server errors pass; anything else (bad key, bad request) is a configuration problem
        if (status == 429 || status >= 500) {
            throw new EmbeddingUnavailableException("Voyage AI answered " + status, null);
        }
        if (status != 200) {
            throw new IllegalStateException("Voyage AI rejected the request with status " + status);
        }
        return parse(response.body(), inputs.size());
    }

    private List<Embedding> parse(String body, int expected) {
        try {
            JsonNode data = json.readTree(body).path("data");
            Embedding[] result = new Embedding[expected];
            for (JsonNode item : data) {
                int index = item.path("index").asInt();
                JsonNode values = item.path("embedding");
                float[] vector = new float[values.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) values.get(i).asDouble();
                }
                result[index] = new Embedding(vector);
            }
            if (Arrays.stream(result).anyMatch(Objects::isNull)) {
                throw new IllegalStateException("Voyage AI returned fewer embeddings than requested");
            }
            return List.of(result);
        } catch (JacksonException | IndexOutOfBoundsException e) {
            throw new IllegalStateException("Unexpected response from Voyage AI", e);
        }
    }
}
