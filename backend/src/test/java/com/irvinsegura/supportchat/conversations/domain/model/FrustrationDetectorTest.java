package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FrustrationDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Esto no sirve para nada",
            "Pésimo servicio, ya les escribí tres veces",
            "Son unos ladrones",
            "Estoy HARTA de esperar",
            "Ya te dije que mi pedido no llega!!!",
            "OTRA VEZ ME COBRARON DOBLE",
            "llevo días esperando y nadie me responde",
            "No me entiendes 😡",
            "This is useless",
            "I already told you, still waiting",
            "Isso é um absurdo, péssimo atendimento",
            "C'est nul, j'en ai marre",
    })
    void upsetMessagesAreNoticed(String message) {
        assertThat(FrustrationDetector.assess(message).frustrated())
                .as("%s → %s", message, FrustrationDetector.assess(message).signals())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "¿Cuánto tarda el envío nacional?",
            "Hola, quiero cambiar la dirección de mi pedido",
            "¿Hacen envíos a Colombia?",
            "Gracias, me ayudó mucho!",
            "¿Me lo pueden enviar otra vez?",
            "Mi número de pedido es 123456",
            "¿Tienen DHL o FEDEX?",
            "No funciona el cupón",
            "Do you ship to Canada?",
            "",
    })
    void ordinaryQuestionsAreNot(String message) {
        assertThat(FrustrationDetector.assess(message).frustrated())
                .as("%s → %s", message, FrustrationDetector.assess(message).signals())
                .isFalse();
    }

    @Test
    void accentsAndCaseDoNotHideAComplaint() {
        assertThat(FrustrationDetector.isFrustrated("PESIMO")).isTrue();
        assertThat(FrustrationDetector.isFrustrated("pésimo")).isTrue();
    }

    @Test
    void aWordInsideAnotherIsNotASignal() {
        // "nul" is French for useless, but "anular" is a normal request
        assertThat(FrustrationDetector.assess("Quiero anular mi pedido").signals()).isEmpty();
    }

    @Test
    void softSignalsNeedCompany() {
        assertThat(FrustrationDetector.assess("otra vez").score()).isEqualTo(1);
        assertThat(FrustrationDetector.isFrustrated("otra vez")).isFalse();
        assertThat(FrustrationDetector.isFrustrated("¿otra vez??? nadie contesta")).isTrue();
    }
}
