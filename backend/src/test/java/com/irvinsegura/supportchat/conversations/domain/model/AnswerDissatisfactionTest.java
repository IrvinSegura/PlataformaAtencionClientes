package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnswerDissatisfactionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "No entendí", "eso no es lo que pregunté", "No me sirvió tu respuesta", "That didn't help",
            "not what I asked", "Não entendi", "Je n'ai pas compris"})
    void theCustomerSaysTheAnswerDidNotHelp(String message) {
        assertThat(AnswerDissatisfaction.saysSo(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mi dirección está mal, ¿la pueden cambiar?", "Gracias", "¿Y a Monterrey?", "No, gracias"})
    void otherMessagesSayNothingOfTheAnswer(String message) {
        assertThat(AnswerDissatisfaction.saysSo(message)).isFalse();
    }

    @Test
    void theSameQuestionAskedAgainIsRecognised() {
        assertThat(AnswerDissatisfaction.asksAgain("¿Cuánto tarda el envío nacional?",
                "cuanto tarda el envio nacional???")).isTrue();
        assertThat(AnswerDissatisfaction.asksAgain("¿Cuánto tarda el envío nacional?",
                "¿Y cuánto tarda un envío nacional entonces?")).isTrue();
    }

    @Test
    void aNewQuestionOnTheSameSubjectIsNotTheSameQuestion() {
        assertThat(AnswerDissatisfaction.asksAgain("¿Cuánto cuesta el envío a Monterrey?",
                "¿Cuánto cuesta el envío a Guadalajara?")).isFalse();
        assertThat(AnswerDissatisfaction.asksAgain("¿Cuánto tarda el envío?", "¿Puedo pagar con tarjeta?"))
                .isFalse();
    }

    @Test
    void questionWordsIgnoreAccentsPluralsAndShortWords() {
        assertThat(QuestionWords.of("¿Cuánto tardan los envíos?")).containsExactly("cuanto", "envio", "tardan");
        assertThat(QuestionWords.of("Devoluciones y colores")).containsExactly("color", "devolucion");
        assertThat(QuestionWords.similarity(QuestionWords.of("hola"), QuestionWords.of("hola"))).isZero();
    }
}
