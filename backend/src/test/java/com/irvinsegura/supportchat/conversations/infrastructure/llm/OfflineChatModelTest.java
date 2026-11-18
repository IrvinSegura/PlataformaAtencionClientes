package com.irvinsegura.supportchat.conversations.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.conversations.domain.model.Source;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfflineChatModelTest {

    private static final Source RETURNS = source("""
            Tienes 30 días naturales para devolver un producto. Para iniciar una devolución escribe a soporte.
            El reembolso se hace en un plazo de 5 a 10 días hábiles.""");
    private static final Source PAYMENTS = source("Aceptamos tarjetas Visa, Mastercard y American Express.");

    @Test
    void answersWhenTheSourceCoversHalfOfTheQuestion() {
        assertThat(OfflineChatModel.covers(RETURNS, "¿Cuánto tarda el reembolso?")).isTrue();
    }

    @Test
    void wordsSharingAStemCount() {
        assertThat(OfflineChatModel.covers(PAYMENTS, "¿Aceptan American Express?")).isTrue();
    }

    @Test
    void declinesWhenTheSourceOnlySharesAPassingWord() {
        assertThat(OfflineChatModel.covers(RETURNS, "Escribe un poema sobre gatos")).isFalse();
    }

    @Test
    void aQuestionWithoutMeaningfulWordsIsLeftToRetrieval() {
        assertThat(OfflineChatModel.covers(RETURNS, "¿Qué es?")).isTrue();
    }

    private static Source source(String content) {
        return new Source(1, UUID.randomUUID(), UUID.randomUUID(), "Doc", content, 0.5, true);
    }
}
