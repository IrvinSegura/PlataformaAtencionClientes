package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.shared.domain.QuestionKey;
import org.junit.jupiter.api.Test;

class QuestionKeyTest {

    @Test
    void accentsCasePunctuationAndSpacingDoNotMakeADifferentQuestion() {
        assertThat(QuestionKey.of("¿Aceptan  pagos con BITCÓIN?"))
                .isEqualTo(QuestionKey.of("aceptan pagos con bitcoin"))
                .isEqualTo("aceptan pagos con bitcoin");
    }

    @Test
    void differentWordsStayDifferent() {
        assertThat(QuestionKey.of("¿Envían a Monterrey?")).isNotEqualTo(QuestionKey.of("¿Envían a Guadalajara?"));
    }

    @Test
    void veryLongQuestionsAreCut() {
        assertThat(QuestionKey.of("a ".repeat(1_000))).hasSizeLessThanOrEqualTo(QuestionKey.MAX_LENGTH);
    }
}
