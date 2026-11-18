package com.irvinsegura.supportchat.conversations.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationHandoffTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final UUID LAURA = UUID.randomUUID();
    private static final UUID PEDRO = UUID.randomUUID();

    private final Conversation conversation = Conversation.start(UUID.randomUUID(), UUID.randomUUID(), new byte[32],
            "https://shop.example.com", NOW);

    @Test
    void askingTwiceForAPersonChangesNothingTheSecondTime() {
        assertThat(conversation.requestHandoff(NOW)).isTrue();
        assertThat(conversation.requestHandoff(NOW)).isFalse();
        assertThat(conversation.handling()).isEqualTo(ConversationHandling.WAITING);
        assertThat(conversation.handoffRequestedAt()).isEqualTo(NOW);

        assertThat(conversation.cancelHandoff(NOW)).isTrue();
        assertThat(conversation.handling()).isEqualTo(ConversationHandling.ASSISTANT);
    }

    @Test
    void onlyQuestionsForTheAssistantCountTowardsTheLimit() {
        conversation.acceptQuestion(1, NOW);
        assertThatThrownBy(() -> conversation.acceptQuestion(1, NOW))
                .isInstanceOf(QuestionLimitReachedException.class);

        conversation.takeOver(LAURA, "Laura", NOW);
        conversation.acceptQuestion(1, NOW);
        assertThat(conversation.questionCount()).isEqualTo(1);
    }

    @Test
    void aConversationHasOneAgentAtATime() {
        assertThat(conversation.takeOver(LAURA, "Laura", NOW)).isTrue();
        assertThat(conversation.takeOver(LAURA, "Laura", NOW)).isFalse();
        assertThatThrownBy(() -> conversation.takeOver(PEDRO, "Pedro", NOW))
                .isInstanceOf(ConversationAssignedException.class);
        assertThatThrownBy(() -> conversation.acceptAgentReply(PEDRO, NOW))
                .isInstanceOf(ConversationAssignedException.class);
        assertThatThrownBy(() -> conversation.release(PEDRO, false, NOW))
                .isInstanceOf(ConversationAssignedException.class);

        assertThat(conversation.release(PEDRO, true, NOW)).isTrue();
        assertThat(conversation.agentId()).isNull();
    }

    @Test
    void replyingNeedsTheConversationTakenOver() {
        assertThatThrownBy(() -> conversation.acceptAgentReply(LAURA, NOW))
                .isInstanceOf(ConversationNotTakenException.class);
    }

    @Test
    void closingEndsTheWaitAndKeepsWhoAnswered() {
        conversation.requestHandoff(NOW);
        assertThat(conversation.close(LAURA, false, NOW)).isTrue();
        assertThat(conversation.handling()).isEqualTo(ConversationHandling.ASSISTANT);
        assertThat(conversation.close(LAURA, false, NOW)).isFalse();
        assertThatThrownBy(() -> conversation.requestHandoff(NOW)).isInstanceOf(ConversationClosedException.class);
        assertThatThrownBy(() -> conversation.acceptQuestion(10, NOW))
                .isInstanceOf(ConversationClosedException.class);
    }
}
