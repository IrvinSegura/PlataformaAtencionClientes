package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelConversationTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final Duration DAY = Duration.ofHours(24);

    @Test
    void aChannelConversationBelongsToAContactAndHasNoToken() {
        Conversation conversation = start();

        assertThat(conversation.channel()).isEqualTo(ConversationChannel.WHATSAPP);
        assertThat(conversation.contactId()).isEqualTo("5215551111");
        assertThat(conversation.visitorTokenHash()).isNull();
        // No token opens it through the widget
        assertThatThrownBy(() -> conversation.authenticate("anything", DAY, NOW))
                .isInstanceOf(InvalidConversationTokenException.class);
    }

    @Test
    void startsOverAfterALongSilenceOrOnceClosed() {
        Conversation conversation = start();

        assertThat(conversation.shouldStartOver(DAY, NOW.plus(Duration.ofHours(23)))).isFalse();
        assertThat(conversation.shouldStartOver(DAY, NOW.plus(DAY))).isTrue();
        conversation.retire(NOW.plusSeconds(1));
        assertThat(conversation.status()).isEqualTo(ConversationStatus.CLOSED);
        assertThat(conversation.shouldStartOver(DAY, NOW.plusSeconds(2))).isTrue();
    }

    @Test
    void usingUpTheQuestionsDoesNotStartOver() {
        Conversation conversation = start();
        conversation.acceptQuestion(1, NOW);

        assertThat(conversation.usedUpQuestions(1)).isTrue();
        assertThat(conversation.shouldStartOver(DAY, NOW.plusSeconds(1))).isFalse();
    }

    @Test
    void theWebNeedsATokenAndOtherChannelsAContact() {
        assertThatThrownBy(() -> Conversation.startOnChannel(UUID.randomUUID(), UUID.randomUUID(),
                ConversationChannel.WEB, "5215551111", null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Conversation.startOnChannel(UUID.randomUUID(), UUID.randomUUID(),
                ConversationChannel.WHATSAPP, " ", null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Conversation start() {
        return Conversation.startOnChannel(UUID.randomUUID(), UUID.randomUUID(), ConversationChannel.WHATSAPP,
                "5215551111", "Lucía", NOW);
    }
}
