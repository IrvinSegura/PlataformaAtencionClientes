package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.conversations.domain.model.SmallTalk.Kind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SmallTalkTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Hola | GREETING",
            "¡Buenos días! | GREETING",
            "hola, ¿qué tal? | GREETING",
            "Hi there? | ",
            "Good morning | GREETING",
            "Olá, tudo bem? | GREETING",
            "Bonjour, ça va ? | GREETING",
            "¡Muchas gracias! | THANKS",
            "Hola, gracias por la ayuda | THANKS",
            "Thank you so much | THANKS",
            "Obrigado | THANKS",
            "merci beaucoup | THANKS",
            "👍 | THANKS",
            "🙏🙏 | THANKS",
            "ok bye | GOODBYE",
            "Adiós, hasta luego | GOODBYE",
            "Tchau | GOODBYE",
            "ok | ACKNOWLEDGEMENT",
            "Perfecto | ACKNOWLEDGEMENT",
            "Vale, entendido | ACKNOWLEDGEMENT",
    })
    void recognisesCourtesy(String message, Kind kind) {
        if (kind == null) {
            assertThat(SmallTalk.of(message)).isEmpty();
        } else {
            assertThat(SmallTalk.of(message)).contains(kind);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Hola, ¿cuánto cuesta el envío?",
            "gracias, ¿y a Monterrey?",
            "ok, ¿y si no estoy en casa?",
            "sí",
            "claro",
            "dale",
            "no",
            "no gracias",
            "?",
            "ok?",
            "¿bien?",
            "...",
            "que",
            "muy",
            "Hola hola hola hola hola hola hola hola hola",
            "Buenas tardes, quiero cambiar mi dirección",
    })
    void anythingElseIsNotCourtesy(String message) {
        assertThat(SmallTalk.of(message)).isEmpty();
    }
}
