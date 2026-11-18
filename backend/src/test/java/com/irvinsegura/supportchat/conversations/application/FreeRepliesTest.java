package com.irvinsegura.supportchat.conversations.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.SystemNotice;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * When a message is answered without a model call: a courtesy reply, or an answer given again.
 */
class FreeRepliesTest {

    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Test
    void aQuestionStandsAloneAfterNothingButCourtesy() {
        assertThat(AnswerReuse.standsAlone(List.of())).isTrue();
        assertThat(AnswerReuse.standsAlone(List.of(
                question("¡Hola!"),
                answer("¡Hola! ¿En qué te puedo ayudar?", AnswerOutcome.SMALL_TALK),
                Message.notice(UUID.randomUUID(), CONVERSATION, SystemNotice.ASSISTANT_PAUSED, null, null, NOW))))
                .isTrue();
    }

    @Test
    void aQuestionAfterAnotherMayDependOnIt() {
        assertThat(AnswerReuse.standsAlone(List.of(
                question("¿Cuánto tarda el envío a Guadalajara?"),
                answer("De 3 a 5 días.", AnswerOutcome.ANSWERED)))).isFalse();
        // The assistant offered a person; "sí" or "¿y a Monterrey?" next means something only here
        assertThat(AnswerReuse.standsAlone(List.of(
                question("Hola"),
                answer("No tengo esa información. ¿Quieres hablar con una persona?", AnswerOutcome.NO_ANSWER))))
                .isFalse();
    }

    @Test
    void anOkToAQuestionOfTheAssistantAnswersIt() {
        assertThat(AnswerLearning.assistantAsked(List.of(
                question("¿Tienen talla 30?"),
                answer("No tengo esa información. ¿Quieres hablar con una persona? 🙂", AnswerOutcome.NO_ANSWER))))
                .isTrue();
        assertThat(AnswerLearning.assistantAsked(List.of(
                question("¿Cuánto tarda el envío?"),
                answer("De 3 a 5 días hábiles [1]", AnswerOutcome.ANSWERED)))).isFalse();
        assertThat(AnswerLearning.assistantAsked(List.of())).isFalse();
    }

    private static Message question(String text) {
        return Message.question(UUID.randomUUID(), CONVERSATION, text, 2000, NOW);
    }

    private static Message answer(String text, AnswerOutcome outcome) {
        return Message.answer(UUID.randomUUID(), CONVERSATION, text, outcome, List.of(), null, NOW);
    }
}
