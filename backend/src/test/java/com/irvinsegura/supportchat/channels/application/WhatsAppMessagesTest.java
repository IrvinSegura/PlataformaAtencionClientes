package com.irvinsegura.supportchat.channels.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.channels.application.ChannelReplies.ButtonPress;
import com.irvinsegura.supportchat.channels.application.WhatsAppPayload.Delivered;
import com.irvinsegura.supportchat.channels.domain.model.InboundWhatsApp;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage.Button;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.conversations.ConversationReply.Kind;
import com.irvinsegura.supportchat.conversations.ConversationReply.PendingAction;
import com.irvinsegura.supportchat.shared.domain.Language;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WhatsAppMessagesTest {

    private static final String DELIVERY = """
            {"object":"whatsapp_business_account","entry":[{"id":"1","changes":[{"field":"messages","value":{
              "messaging_product":"whatsapp",
              "metadata":{"display_phone_number":"5215550000","phone_number_id":"1234567890"},
              "contacts":[{"profile":{"name":"Lucía"},"wa_id":"5215551111"}],
              "messages":[
                {"from":"5215551111","id":"wamid.1","timestamp":"1","type":"text","text":{"body":"Hola"}},
                {"from":"5215551111","id":"wamid.2","timestamp":"2","type":"interactive",
                 "interactive":{"type":"button_reply","button_reply":{"id":"confirm:x","title":"Confirmar"}}},
                {"from":"5215551111","id":"wamid.3","timestamp":"3","type":"image","image":{"id":"m"}}
              ],
              "statuses":[{"id":"wamid.0","status":"delivered"}]
            }}]}]}""";

    @Test
    void readsTextsAndButtonPressesAndSkipsTheRest() {
        List<Delivered> delivered = WhatsAppPayload.read(JsonMapper.builder().build().readTree(DELIVERY));

        assertThat(delivered).containsExactly(
                new Delivered("1234567890", new InboundWhatsApp("wamid.1", "5215551111", "Lucía", "Hola", null)),
                new Delivered("1234567890",
                        new InboundWhatsApp("wamid.2", "5215551111", "Lucía", null, "confirm:x")));
    }

    @Test
    void anAnswerListsItsSources() {
        ConversationReply reply = reply(Kind.ASSISTANT, "Llega en 3 días [1].", null, List.of("[1] Envíos"), null);

        assertThat(compose(reply)).contains(
                OutboundMessage.text("5215551111", "Llega en 3 días [1].\n\nFuentes: [1] Envíos"));
    }

    @Test
    void aProposedActionComesWithConfirmAndCancelButtons() {
        UUID runId = UUID.randomUUID();
        ConversationReply reply = reply(Kind.ASSISTANT, "Cambiaré la dirección.", null, List.of(),
                new PendingAction(runId, "Cambiar dirección", Map.of("address", "Calle 1")));

        OutboundMessage message = compose(reply).orElseThrow();

        assertThat(message.text()).isEqualTo("Cambiaré la dirección.\n\n*Cambiar dirección*\n• address: Calle 1");
        assertThat(message.buttons()).containsExactly(
                new Button("confirm:" + runId, "Confirmar"), new Button("cancel:" + runId, "Cancelar"));
        assertThat(ChannelReplies.button("confirm:" + runId)).contains(new ButtonPress(runId, true));
        assertThat(ChannelReplies.button("cancel:" + runId)).contains(new ButtonPress(runId, false));
        assertThat(ChannelReplies.button("confirm:not-a-uuid")).isEmpty();
    }

    @Test
    void noticesAreWordedForTheCustomer() {
        assertThat(compose(reply(Kind.NOTICE, "AGENT_JOINED", "Marta", List.of(), null)))
                .map(OutboundMessage::text).contains("Marta se unió a la conversación.");
        assertThat(compose(reply(Kind.AGENT, "Ya lo reviso", "Marta", List.of(), null)))
                .map(OutboundMessage::text).contains("*Marta*: Ya lo reviso");
        assertThat(compose(reply(Kind.NOTICE, "SOMETHING_NEW", null, List.of(), null))).isEmpty();
    }

    @Test
    void labelsAndNoticesFollowTheCustomersLanguage() {
        UUID runId = UUID.randomUUID();
        OutboundMessage answer = compose(reply(Kind.ASSISTANT, "I'll change the address [1].", null,
                List.of("[1] Shipping"), new PendingAction(runId, "Change address", Map.of("address", "1 Main St")),
                Language.EN)).orElseThrow();

        assertThat(answer.text()).startsWith("I'll change the address [1].\n\nSources: [1] Shipping");
        assertThat(answer.buttons()).extracting(Button::title).containsExactly("Confirm", "Cancel");
        assertThat(compose(reply(Kind.NOTICE, "AGENT_JOINED", null, List.of(), null, Language.EN)))
                .map(OutboundMessage::text).contains("Someone from the team joined the conversation.");
        assertThat(compose(reply(Kind.NOTICE, "CONVERSATION_CLOSED", null, List.of(), null,
                Language.PT))).map(OutboundMessage::text)
                .contains("A conversa foi encerrada. Se precisar de mais alguma coisa, escreva de novo.");
        assertThat(compose(reply(Kind.NOTICE, "AGENT_LEFT", "Marie", List.of(), null, Language.FR)))
                .map(OutboundMessage::text)
                .contains("Marie a quitté la conversation ; l'assistant reste avec vous.");
    }

    private static Optional<OutboundMessage> compose(ConversationReply reply) {
        return ChannelReplies.compose(reply, ChannelReplies.Format.WHATSAPP);
    }

    private static ConversationReply reply(Kind kind, String text, String author, List<String> sources,
            PendingAction pending) {
        return reply(kind, text, author, sources, pending, Language.ES);
    }

    private static ConversationReply reply(Kind kind, String text, String author, List<String> sources,
            PendingAction pending, Language language) {
        return new ConversationReply(UUID.randomUUID(), UUID.randomUUID(), ConversationChannel.WHATSAPP,
                "5215551111", UUID.randomUUID(), kind, text, author, sources, pending, language);
    }
}
