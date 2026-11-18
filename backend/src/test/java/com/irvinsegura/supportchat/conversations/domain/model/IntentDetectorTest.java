package com.irvinsegura.supportchat.conversations.domain.model;

import static com.irvinsegura.supportchat.conversations.ConversationIntent.ACCOUNT;
import static com.irvinsegura.supportchat.conversations.ConversationIntent.AVAILABILITY;
import static com.irvinsegura.supportchat.conversations.ConversationIntent.COMPLAINT;
import static com.irvinsegura.supportchat.conversations.ConversationIntent.HOURS_AND_LOCATION;
import static com.irvinsegura.supportchat.conversations.ConversationIntent.ORDER_STATUS;
import static com.irvinsegura.supportchat.conversations.ConversationIntent.PAYMENTS;
import static com.irvinsegura.supportchat.conversations.ConversationIntent.PRICING;
import static com.irvinsegura.supportchat.conversations.ConversationIntent.RETURNS;
import static com.irvinsegura.supportchat.conversations.ConversationIntent.SHIPPING;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntentDetectorTest {

    @Test
    void recognizesTheTopicsOfAMessageInAnyCaseAndWithoutAccents() {
        assertThat(IntentDetector.of("¿Cuánto cuesta el ENVIO a Monterrey?")).containsExactlyInAnyOrder(PRICING,
                SHIPPING);
        assertThat(IntentDetector.of("Mi pedido 1234 no ha llegado")).containsExactly(ORDER_STATUS);
        assertThat(IntentDetector.of("Quiero devolver unos zapatos y que me reembolsen")).containsExactly(RETURNS);
        assertThat(IntentDetector.of("¿Aceptan pagos con tarjeta? Necesito factura")).containsExactly(PAYMENTS);
        assertThat(IntentDetector.of("¿Tienen la talla 7 disponible?")).containsExactly(AVAILABILITY);
        assertThat(IntentDetector.of("¿A qué hora abren la sucursal del centro?"))
                .containsExactly(HOURS_AND_LOCATION);
        assertThat(IntentDetector.of("Olvidé mi contraseña")).containsExactly(ACCOUNT);
        assertThat(IntentDetector.of("Quiero poner una queja")).containsExactly(COMPLAINT);
    }

    @Test
    void understandsEnglishPortugueseAndFrench() {
        assertThat(IntentDetector.of("Where is my order? I need the tracking number")).containsExactly(ORDER_STATUS);
        assertThat(IntentDetector.of("Qual é o prazo do frete?")).containsExactly(SHIPPING);
        assertThat(IntentDetector.of("Je voudrais un remboursement")).containsExactly(RETURNS);
        assertThat(IntentDetector.of("Is this sold out?")).containsExactly(AVAILABILITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hola, buenas tardes", "Tengo en cuenta lo que dijiste", "Visité la pagoda",
            "Our relationship matters", "", "   "})
    void leavesMessagesWithoutAClearTopicUntagged(String text) {
        assertThat(IntentDetector.of(text)).isEmpty();
    }
}
