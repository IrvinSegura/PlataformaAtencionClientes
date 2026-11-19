package com.irvinsegura.supportchat.channels.infrastructure.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage.Button;
import com.irvinsegura.supportchat.channels.domain.model.TemplateMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphWhatsAppSenderTest {

    @Test
    void longTextsAreCutToWhatWhatsAppAccepts() {
        String text = "a".repeat(5000);

        Map<?, ?> plain = (Map<?, ?>) GraphWhatsAppSender.body(OutboundMessage.text("1", text)).get("text");
        Map<?, ?> interactive = (Map<?, ?>) GraphWhatsAppSender.body(
                new OutboundMessage("1", text, List.of(new Button("confirm:1", "Confirmar")))).get("interactive");

        assertThat((String) plain.get("body")).hasSize(GraphWhatsAppSender.MAX_TEXT).endsWith("…");
        assertThat((String) ((Map<?, ?>) interactive.get("body")).get("text"))
                .hasSize(GraphWhatsAppSender.MAX_BUTTON_TEXT).endsWith("…");
    }

    @Test
    void mexicanMobilesAreSentToWithoutTheOldOne() {
        assertThat(GraphWhatsAppSender.body(OutboundMessage.text("5215512345678", "Hola"))).containsEntry("to",
                "525512345678");
        // Only a Mexican mobile with its full ten digits loses the 1; any other number goes as WhatsApp reported it
        assertThat(GraphWhatsAppSender.recipient("525512345678")).isEqualTo("525512345678");
        assertThat(GraphWhatsAppSender.recipient("5215551111")).isEqualTo("5215551111");
        assertThat(GraphWhatsAppSender.recipient("14155550100")).isEqualTo("14155550100");
        assertThat(GraphWhatsAppSender.recipient("5491112345678")).isEqualTo("5491112345678");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTemplateCarriesItsLanguageAndBodyVariables() {
        Map<String, Object> body = GraphWhatsAppSender.body(new TemplateMessage("5215550000001", "alerta_cliente",
                "es_MX", List.of("Lucía (WhatsApp +5215551111)", "parece\nmolesta", "")));

        assertThat(body).containsEntry("type", "template").containsEntry("to", "525550000001");
        Map<?, ?> template = (Map<?, ?>) body.get("template");
        assertThat(template.get("name")).isEqualTo("alerta_cliente");
        assertThat(template.get("language")).isEqualTo(Map.of("code", "es_MX"));
        List<Object> parameters = (List<Object>) ((Map<?, ?>) ((List<?>) template.get("components")).getFirst())
                .get("parameters");
        assertThat(parameters).containsExactly(
                Map.of("type", "text", "text", "Lucía (WhatsApp +5215551111)"),
                // Meta refuses line breaks and empty values in a variable
                Map.of("type", "text", "text", "parece molesta"),
                Map.of("type", "text", "text", "-"));
    }
}
