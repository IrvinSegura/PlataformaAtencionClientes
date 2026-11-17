package com.irvinsegura.supportchat.knowledge.infrastructure.embedding;

import com.irvinsegura.supportchat.knowledge.domain.port.EmbeddingModel;
import java.net.http.HttpClient;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Factory for the embedding adapters, which stay package-private.
 */
public final class EmbeddingModels {

    private EmbeddingModels() {
    }

    public static EmbeddingModel hashing() {
        return new HashingEmbeddingModel();
    }

    public static EmbeddingModel voyage(HttpClient http, JsonMapper json, String baseUrl, String apiKey,
            String model, Duration timeout) {
        return new VoyageEmbeddingModel(http, json, baseUrl, apiKey, model, timeout);
    }
}
