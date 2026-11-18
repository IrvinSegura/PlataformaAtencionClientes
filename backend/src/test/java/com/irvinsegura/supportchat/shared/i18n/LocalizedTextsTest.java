package com.irvinsegura.supportchat.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.shared.domain.Language;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalizedTextsTest {

    @ParameterizedTest
    @ValueSource(strings = {"widget", "channels", "replies", "owner-alerts"})
    void everyLanguageOfABundleHasTheSameTextsWithTheSamePlaceholders(String bundle) {
        LocalizedTexts texts = LocalizedTexts.load(bundle);
        Language reference = texts.languages().iterator().next();
        Map<String, Long> expected = placeholders(texts.in(reference));
        for (Language language : texts.languages()) {
            assertThat(placeholders(texts.in(language))).as(bundle + "/" + language.code()).isEqualTo(expected);
            assertThat(texts.in(language).values()).as(bundle + "/" + language.code())
                    .allSatisfy(text -> assertThat(text).isNotBlank());
        }
    }

    @Test
    void theCustomersLanguagesAreAllThere() {
        assertThat(LocalizedTexts.load("channels").languages()).containsExactlyInAnyOrder(Language.values());
        assertThat(LocalizedTexts.load("replies").languages()).containsExactlyInAnyOrder(Language.values());
    }

    @Test
    void fillsThePlaceholdersInOrderAndLeavesTheArgumentsAlone() {
        assertThat(LocalizedTexts.fill("{} de {}", 3, 10)).isEqualTo("3 de 10");
        assertThat(LocalizedTexts.fill("«{}»", "¿{}?")).isEqualTo("«¿{}?»");
        assertThat(LocalizedTexts.fill("sin nada")).isEqualTo("sin nada");
    }

    @Test
    void aMissingTextIsAMistakeNotAnEmptyMessage() {
        LocalizedTexts texts = LocalizedTexts.load("channels");

        assertThatThrownBy(() -> texts.text(Language.ES, "nope")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LocalizedTexts.load("nope")).isInstanceOf(IllegalStateException.class);
    }

    private static Map<String, Long> placeholders(Map<String, String> texts) {
        Map<String, Long> counts = new TreeMap<>();
        texts.forEach((key, text) -> counts.put(key, (long) text.split("\\{}", -1).length - 1));
        return counts;
    }
}
