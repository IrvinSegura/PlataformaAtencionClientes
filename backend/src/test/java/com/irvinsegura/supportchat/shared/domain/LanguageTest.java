package com.irvinsegura.supportchat.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "¿Cuánto tarda el envío nacional?|ES",
            "Hola|ES",
            "quiero cambiar la direccion de mi pedido|ES",
            "How long does shipping take?|EN",
            "Hello|EN",
            "do you ship to canada|EN",
            "Quanto tempo demora a entrega do meu pedido?|PT",
            "Vocês aceitam cartão?|PT",
            "Olá|PT",
            "Combien de temps pour la livraison ?|FR",
            "Bonjour, où est ma commande ?|FR",
    })
    void recognizesTheLanguageOfACustomersMessage(String text, Language expected) {
        assertThat(Language.detect(text)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ok", "123456", "👍", "de", "pedido"})
    void saysNothingWhenTheMessageDoesNotTell(String text) {
        assertThat(Language.detect(text)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "en-US,en;q=0.9,es;q=0.8|EN",
            "de-DE,de;q=0.9,fr;q=0.7|FR",
            "es-MX|ES",
            "pt-BR|PT",
    })
    void picksTheFirstSpokenLanguageTheBrowserPrefers(String header, Language expected) {
        assertThat(Language.preferred(header)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"de-DE,ja;q=0.5", "not a header;;;", "*"})
    void noPreferenceItSpeaks(String header) {
        assertThat(Language.preferred(header)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"es,ES", "EN-gb,EN", "pt_BR,PT", "fr,FR"})
    void readsCodes(String code, Language expected) {
        assertThat(Language.ofCode(code)).contains(expected);
    }
}
