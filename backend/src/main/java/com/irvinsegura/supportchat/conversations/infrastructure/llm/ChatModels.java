package com.irvinsegura.supportchat.conversations.infrastructure.llm;

import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Factory for the chat model adapters, which stay package-private.
 */
public final class ChatModels {

    private ChatModels() {
    }

    public static ChatModel offline() {
        return new OfflineChatModel();
    }

    /**
     * @param models the model, and how much it thinks, for each tier
     */
    public static ChatModel anthropic(HttpClient http, JsonMapper json, String baseUrl, String apiKey,
            Map<ModelTier, TierModel> models, int maxOutputTokens, Duration readTimeout) {
        return new AnthropicChatModel(http, json, baseUrl, apiKey, models, maxOutputTokens, readTimeout);
    }

    /**
     * @param effort {@code low}, {@code medium} or {@code high}
     */
    public record TierModel(String model, String effort) {
    }
}
