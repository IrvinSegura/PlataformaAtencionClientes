package com.irvinsegura.supportchat.conversations.domain.port;

import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolCall;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import java.util.List;
import java.util.function.Consumer;

/**
 * A language model that streams its answer. If {@code onText} throws (e.g. the visitor disconnected), the model
 * must stop generating, release the connection and let the exception propagate.
 *
 * @see com.irvinsegura.supportchat.conversations.domain.model.ChatModelUnavailableException thrown when the
 * provider is temporarily unavailable
 */
public interface ChatModel {

    /**
     * @param tier how capable (and expensive) a model the question needs; each adapter maps it to one of its models
     */
    Completion stream(ChatPrompt prompt, ModelTier tier, Consumer<String> onText);

    /**
     * The model this adapter answers a tier with, as shown to businesses choosing one.
     */
    String modelName(ModelTier tier);

    /**
     * @param model     the exact model that answered, as the provider reports it
     * @param toolCalls actions the model wants run before it goes on; empty when the answer is complete
     * @param replay    the provider's encoding of this turn, to send back unchanged with the tool results; may be null
     */
    record Completion(String model, int inputTokens, int outputTokens, List<ToolCall> toolCalls, String replay) {

        public Completion {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        public Completion(String model, int inputTokens, int outputTokens) {
            this(model, inputTokens, outputTokens, List.of(), null);
        }
    }
}
