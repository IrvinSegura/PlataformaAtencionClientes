package com.irvinsegura.supportchat.conversations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.conversations.application.ConversationSettings.Replies;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter;
import com.irvinsegura.supportchat.conversations.domain.model.SourceSelection;
import com.irvinsegura.supportchat.shared.domain.Language;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationSettingsTest {

    private static final Replies SPANISH = replies("No sé");
    private static final Replies ENGLISH = replies("I don't know");

    @Test
    void repliesInTheConversationsLanguageOrTheDefaultOne() {
        ConversationSettings settings = settings(Map.of(Language.ES, SPANISH, Language.EN, ENGLISH));
        Conversation conversation = Conversation.start(UUID.randomUUID(), UUID.randomUUID(), new byte[32], null,
                Instant.now());

        assertThat(settings.languageOf(conversation)).isEqualTo(Language.ES);
        conversation.speak(Language.EN);
        assertThat(settings.replies(settings.languageOf(conversation))).isEqualTo(ENGLISH);
        // Nothing configured for French: the default language's replies
        assertThat(settings.replies(Language.FR)).isEqualTo(SPANISH);
        conversation.speak(null);
        assertThat(conversation.language()).isEqualTo(Language.EN);
    }

    @Test
    void theDefaultLanguageMustHaveReplies() {
        assertThatThrownBy(() -> settings(Map.of(Language.EN, ENGLISH))).isInstanceOf(IllegalArgumentException.class);
    }

    private static ConversationSettings settings(Map<Language, Replies> replies) {
        return new ConversationSettings(new SourceSelection(0.2, 5), 6, 2000, 40, Duration.ofHours(24),
                new ModelRouter(true), true, 3, Language.ES, replies);
    }

    private static Replies replies(String noAnswer) {
        return new Replies(noAnswer, "failure", "confirm", "done", "failed", "cancelled", "expired", "greeting",
                "thanks", "goodbye", "acknowledged");
    }
}
