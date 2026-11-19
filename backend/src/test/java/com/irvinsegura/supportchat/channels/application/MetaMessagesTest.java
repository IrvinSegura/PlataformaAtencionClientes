package com.irvinsegura.supportchat.channels.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.supportchat.channels.application.MetaPayload.Delivered;
import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage.Button;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.conversations.ConversationReply.Kind;
import com.irvinsegura.supportchat.conversations.ConversationReply.PendingAction;
import com.irvinsegura.supportchat.shared.domain.Language;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MetaMessagesTest {

    private static final String DELIVERY = """
            {"object":"page","entry":[{"id":"1000001","time":1,"messaging":[
              {"sender":{"id":"2001"},"recipient":{"id":"1000001"},"message":{"mid":"m.1","text":"Hola"}},
              {"sender":{"id":"2001"},"recipient":{"id":"1000001"},
               "message":{"mid":"m.2","text":"Confirmar","quick_reply":{"payload":"confirm:x"}}},
              {"sender":{"id":"2001"},"recipient":{"id":"1000001"},
               "postback":{"mid":"m.3","title":"Empezar","payload":"GET_STARTED"}},
              {"sender":{"id":"1000001"},"recipient":{"id":"2001"},
               "message":{"mid":"m.4","text":"Respuesta de la página","is_echo":true}},
              {"sender":{"id":"2001"},"recipient":{"id":"1000001"},"message":{"mid":"m.5","is_echo":true,"text":"x"}},
              {"sender":{"id":"2001"},"recipient":{"id":"1000001"},"read":{"watermark":1}},
              {"sender":{"id":"2001"},"recipient":{"id":"1000001"},
               "message":{"mid":"m.6","attachments":[{"type":"image"}]}}
            ]}]}""";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void readsTextsQuickRepliesAndPostbacksAndSkipsTheRest() {
        List<Delivered> delivered = MetaPayload.read(JSON.readTree(DELIVERY), MetaPlatform.MESSENGER);

        assertThat(delivered).containsExactly(
                new Delivered("1000001", "m.1", "2001", "Hola", null),
                new Delivered("1000001", "m.2", "2001", null, "confirm:x"),
                new Delivered("1000001", "m.3", "2001", null, "GET_STARTED"));
    }

    @Test
    void aDeliveryForTheOtherPlatformIsIgnored() {
        JsonNode root = JSON.readTree(DELIVERY);

        assertThat(MetaPayload.read(root, MetaPlatform.INSTAGRAM)).isEmpty();
        assertThat(MetaPayload.read(JSON.readTree(DELIVERY.replace("\"page\"", "\"instagram\"")),
                MetaPlatform.INSTAGRAM)).hasSize(3);
        assertThat(MetaPayload.read(JSON.readTree("{\"object\":\"user\",\"entry\":[]}"), MetaPlatform.MESSENGER))
                .isEmpty();
    }

    @Test
    void plainChannelsShowNoAsterisks() {
        UUID runId = UUID.randomUUID();
        ConversationReply reply = reply(Kind.ASSISTANT, "Cambiaré la dirección.", null,
                new PendingAction(runId, "Cambiar dirección", Map.of("address", "Calle 1")));

        OutboundMessage message = ChannelReplies.compose(reply, ChannelReplies.Format.PLAIN).orElseThrow();

        assertThat(message.text()).isEqualTo("Cambiaré la dirección.\n\nCambiar dirección\n• address: Calle 1");
        assertThat(message.buttons()).containsExactly(
                new Button("confirm:" + runId, "Confirmar"), new Button("cancel:" + runId, "Cancelar"));
        assertThat(ChannelReplies.compose(reply(Kind.AGENT, "Ya lo reviso", "Marta", null),
                ChannelReplies.Format.PLAIN)).map(OutboundMessage::text).contains("Marta: Ya lo reviso");
    }

    private static ConversationReply reply(Kind kind, String text, String author, PendingAction pending) {
        return new ConversationReply(UUID.randomUUID(), UUID.randomUUID(), ConversationChannel.MESSENGER, "2001",
                UUID.randomUUID(), kind, text, author, List.of(), pending, Language.ES);
    }
}
