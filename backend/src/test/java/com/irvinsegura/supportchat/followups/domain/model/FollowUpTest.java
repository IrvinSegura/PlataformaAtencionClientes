package com.irvinsegura.supportchat.followups.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate.WhatsAppTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FollowUpTest {

    private static final Instant NOW = Instant.parse("2026-11-11T18:00:00Z");
    private static final WhatsAppTemplate APPROVED = new WhatsAppTemplate("seguimiento_pedido", "es_MX", true);

    @Test
    void writesTheCustomersNameWhereThePlaceholderIs() {
        FollowUp followUp = schedule(ConversationChannel.WHATSAPP, template(APPROVED));

        assertThat(followUp.textFor("Lucía")).isEqualTo("Hola Lucía, ¿te llegó tu pedido?");
        assertThat(followUp.textFor(null)).isEqualTo("Hola, ¿te llegó tu pedido?");
    }

    @Test
    void whatsAppTakesTheTextWithin24HoursAndTheApprovedTemplateAfter() {
        FollowUp followUp = schedule(ConversationChannel.WHATSAPP, template(APPROVED));

        assertThat(followUp.route(ConversationChannel.WHATSAPP, NOW.minus(Duration.ofHours(23)), NOW)).isEmpty();
        assertThat(followUp.route(ConversationChannel.WHATSAPP, NOW.minus(Duration.ofHours(25)), NOW))
                .contains(APPROVED);
    }

    @Test
    void withoutAnApprovedTemplateWhatsAppAndMetaRefuseAfter24Hours() {
        FollowUp followUp = schedule(ConversationChannel.WHATSAPP, template(null));
        Instant longAgo = NOW.minus(Duration.ofDays(3));

        assertThatThrownBy(() -> followUp.route(ConversationChannel.WHATSAPP, longAgo, NOW))
                .isInstanceOfSatisfying(FollowUpUndeliverableException.class, e ->
                        assertThat(e.code()).isEqualTo(FollowUpUndeliverableException.WINDOW_CLOSED_NO_TEMPLATE));
        assertThatThrownBy(() -> followUp.route(ConversationChannel.MESSENGER, longAgo, NOW))
                .isInstanceOfSatisfying(FollowUpUndeliverableException.class, e ->
                        assertThat(e.code()).isEqualTo(FollowUpUndeliverableException.WINDOW_CLOSED));
        // Email has no such window
        assertThat(followUp.route(ConversationChannel.EMAIL, longAgo, NOW)).isEmpty();
        assertThat(followUp.route(ConversationChannel.EMAIL, null, NOW)).isEmpty();
    }

    @Test
    void refusesWebVisitorsMomentsInThePastOrTooFarAndEmptyTexts() {
        assertThatThrownBy(() -> schedule(ConversationChannel.WEB, "Hola", NOW))
                .hasFieldOrPropertyWithValue("reason", InvalidFollowUpException.Reason.WEB_VISITOR);
        assertThatThrownBy(() -> schedule(ConversationChannel.WHATSAPP, "Hola", NOW.minus(Duration.ofHours(1))))
                .hasFieldOrPropertyWithValue("reason", InvalidFollowUpException.Reason.DUE_AT);
        assertThatThrownBy(() -> schedule(ConversationChannel.WHATSAPP, "Hola", NOW.plus(Duration.ofDays(91))))
                .hasFieldOrPropertyWithValue("reason", InvalidFollowUpException.Reason.DUE_AT);
        assertThatThrownBy(() -> schedule(ConversationChannel.WHATSAPP, "  ", NOW))
                .hasFieldOrPropertyWithValue("reason", InvalidFollowUpException.Reason.TEXT);
    }

    private static FollowUp schedule(ConversationChannel channel, String text, Instant dueAt) {
        return FollowUp.schedule(UUID.randomUUID(), UUID.randomUUID(), channel, null, text, dueAt, UUID.randomUUID(),
                "Ana", NOW);
    }

    @Test
    void onlyAScheduledFollowUpCanBeCancelled() {
        FollowUp followUp = schedule(ConversationChannel.EMAIL, template(null));
        followUp.cancel(NOW);

        assertThat(followUp.status()).isEqualTo(FollowUpStatus.CANCELLED);
        assertThat(followUp.isDue(NOW.plus(Duration.ofDays(2)))).isFalse();
        assertThatThrownBy(() -> followUp.cancel(NOW)).isInstanceOf(FollowUpNotScheduledException.class);
    }

    @Test
    void onlyASentFollowUpBecomesUndelivered() {
        FollowUp scheduled = schedule(ConversationChannel.WHATSAPP, template(null));
        assertThat(scheduled.notDelivered(FollowUpUndeliverableException.DELIVERY_REFUSED, NOW)).isFalse();
        assertThat(scheduled.status()).isEqualTo(FollowUpStatus.SCHEDULED);

        FollowUp sent = schedule(ConversationChannel.WHATSAPP, template(null));
        UUID conversation = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        sent.sent(conversation, message, NOW);
        assertThat(sent.notDelivered(FollowUpUndeliverableException.DELIVERY_REFUSED, NOW)).isTrue();

        assertThat(sent.status()).isEqualTo(FollowUpStatus.FAILED);
        assertThat(sent.failure()).isEqualTo(FollowUpUndeliverableException.DELIVERY_REFUSED);
        // It still points at the message it went out as
        assertThat(sent.messageId()).isEqualTo(message);
        assertThat(sent.sentConversationId()).isEqualTo(conversation);
        assertThat(sent.notDelivered(FollowUpUndeliverableException.CHANNEL_UNAVAILABLE, NOW)).isFalse();
    }

    @Test
    void anApprovedTemplateNeedsAWhatsAppNameAndLanguage() {
        assertThatThrownBy(() -> new WhatsAppTemplate("Seguimiento Pedido", "es_MX", false))
                .isInstanceOf(InvalidFollowUpTemplateException.class);
        assertThatThrownBy(() -> new WhatsAppTemplate("seguimiento", "español", false))
                .isInstanceOf(InvalidFollowUpTemplateException.class);
        assertThat(new WhatsAppTemplate("seguimiento", "es", false).language()).isEqualTo("es");
    }

    private static FollowUpTemplate template(WhatsAppTemplate whatsapp) {
        return FollowUpTemplate.of(UUID.randomUUID(), "Pedido entregado", "Hola {nombre}, ¿te llegó tu pedido?",
                whatsapp, NOW, NOW);
    }

    private static FollowUp schedule(ConversationChannel channel, FollowUpTemplate template) {
        return FollowUp.schedule(UUID.randomUUID(), UUID.randomUUID(), channel, template, null,
                NOW.plus(Duration.ofDays(3)), UUID.randomUUID(), "Ana", NOW);
    }
}
