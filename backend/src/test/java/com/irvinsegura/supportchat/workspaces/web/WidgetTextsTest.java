package com.irvinsegura.supportchat.workspaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.shared.domain.Language;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WidgetTextsTest {

    private final WidgetTexts texts = new WidgetTexts();

    @Test
    void everyLanguageTranslatesTheSameTextsWithTheSamePlaceholders() {
        Map<String, String> english = texts.in(Language.EN);
        assertThat(english).hasSizeGreaterThan(30).containsEntry("send", "Send");
        for (Language language : Set.of(Language.PT, Language.FR)) {
            Map<String, String> translated = texts.in(language);
            assertThat(translated.keySet()).as(language.code()).isEqualTo(english.keySet());
            assertThat(withPlaceholder(translated)).as(language.code()).isEqualTo(withPlaceholder(english));
            assertThat(translated.values()).as(language.code()).allSatisfy(text -> assertThat(text).isNotBlank());
        }
    }

    @Test
    void textsAreSentUnderTheNamesTheWidgetGivesThem() {
        assertThat(texts.in(Language.EN))
                .containsEntry("inputLabel", "Type your question")
                .containsEntry("notices.AGENT_JOINED", "{} joined the conversation.")
                .doesNotContainKey("input-label");
        assertThat(WidgetTexts.widgetKey("back-to-assistant")).isEqualTo("backToAssistant");
        assertThat(WidgetTexts.widgetKey("notices.handoff-requested")).isEqualTo("notices.HANDOFF_REQUESTED");
    }

    @Test
    void theWidgetsOwnLanguageNeedsNoTexts() {
        assertThat(texts.in(WidgetTexts.BUILT_IN)).isEmpty();
    }

    private static Set<String> withPlaceholder(Map<String, String> texts) {
        return texts.entrySet().stream().filter(entry -> entry.getValue().contains("{}")).map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
