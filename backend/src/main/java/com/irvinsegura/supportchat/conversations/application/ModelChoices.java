package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.ModelPreference;
import com.irvinsegura.supportchat.conversations.domain.model.ModelSelection;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns the model a business chose into the strategy that picks the model tier, and lists the choices with the
 * models behind them for the panel.
 */
@Component
public class ModelChoices {

    private final ConversationSettings settings;
    private final ChatModel chatModel;

    ModelChoices(ConversationSettings settings, ChatModel chatModel) {
        this.settings = settings;
        this.chatModel = chatModel;
    }

    ModelSelection selection(ModelPreference preference) {
        return switch (preference == null ? ModelPreference.AUTOMATIC : preference) {
            case AUTOMATIC -> settings.router();
            case FAST -> ModelSelection.fixed(ModelTier.SIMPLE);
            case PRECISE -> ModelSelection.fixed(ModelTier.COMPLEX);
        };
    }

    /**
     * Every choice with the models it may answer with: both tiers for the automatic one.
     */
    public List<ModelChoiceView> available() {
        return Arrays.stream(ModelPreference.values())
                .map(preference -> new ModelChoiceView(preference, switch (preference) {
                    case AUTOMATIC -> List.of(chatModel.modelName(ModelTier.SIMPLE),
                            chatModel.modelName(ModelTier.COMPLEX)).stream().distinct().toList();
                    case FAST -> List.of(chatModel.modelName(ModelTier.SIMPLE));
                    case PRECISE -> List.of(chatModel.modelName(ModelTier.COMPLEX));
                }))
                .toList();
    }

    public record ModelChoiceView(ModelPreference preference, List<String> models) {
    }
}
