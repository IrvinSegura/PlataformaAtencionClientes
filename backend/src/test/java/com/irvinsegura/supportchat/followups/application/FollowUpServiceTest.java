package com.irvinsegura.supportchat.followups.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.irvinsegura.supportchat.channels.ReplyNotDelivered;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.conversations.CustomerMessages;
import com.irvinsegura.supportchat.conversations.CustomerMessages.Recipient;
import com.irvinsegura.supportchat.followups.domain.model.FollowUp;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpStatus;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpTemplate.WhatsAppTemplate;
import com.irvinsegura.supportchat.followups.domain.model.FollowUpUndeliverableException;
import com.irvinsegura.supportchat.followups.domain.port.FollowUpRepository;
import com.irvinsegura.supportchat.identity.OperatorDirectory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * How the send loop treats a follow-up it cannot send. The repository keeps follow-ups in memory and claims the oldest
 * one still scheduled, as the database does after a rolled-back send.
 */
class FollowUpServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T18:00:00Z");
    private static final UUID WORKSPACE = UUID.randomUUID();

    private final InMemoryFollowUps followUps = new InMemoryFollowUps();
    private final CustomerMessages customers = mock(CustomerMessages.class);
    private final FollowUpService service = new FollowUpService(followUps, customers, mock(OperatorDirectory.class),
            mock(PlatformTransactionManager.class), Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC));

    @Test
    void aFollowUpThatFailsUnexpectedlyIsMarkedFailedAndTheNextOneStillGoesOut() {
        FollowUp broken = schedule(ConversationChannel.EMAIL, null, "Primero");
        FollowUp next = schedule(ConversationChannel.EMAIL, null, "Segundo");
        recipient(broken, ConversationChannel.EMAIL, "Lucía", NOW);
        recipient(next, ConversationChannel.EMAIL, "Lucía", NOW);
        when(customers.sendAsMember(eq(broken.conversationId()), any(), any(), anyString(), any(), any()))
                .thenThrow(new IllegalStateException("database went away"));
        when(customers.sendAsMember(eq(next.conversationId()), any(), any(), anyString(), any(), any()))
                .thenReturn(UUID.randomUUID());

        assertThat(service.sendDue(WORKSPACE)).isEqualTo(2);

        assertThat(broken.status()).isEqualTo(FollowUpStatus.FAILED);
        assertThat(broken.failure()).isEqualTo(FollowUpUndeliverableException.SEND_ERROR);
        assertThat(next.status()).isEqualTo(FollowUpStatus.SENT);
    }

    @Test
    void aTemplateThatSaysOnlyTheUnknownNameFailsWithoutSendingAnything() {
        FollowUp onlyName = schedule(ConversationChannel.EMAIL, null, "{nombre}");
        recipient(onlyName, ConversationChannel.EMAIL, null, NOW);

        assertThat(service.sendDue(WORKSPACE)).isEqualTo(1);

        assertThat(onlyName.failure()).isEqualTo(FollowUpUndeliverableException.NO_TEXT);
        verify(customers, never()).sendAsMember(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aFollowUpWhoseConversationIsGoneFails() {
        FollowUp orphan = schedule(ConversationChannel.EMAIL, null, "Hola");
        when(customers.recipientOf(orphan.conversationId())).thenReturn(Optional.empty());

        assertThat(service.sendDue(WORKSPACE)).isEqualTo(1);

        assertThat(orphan.failure()).isEqualTo(FollowUpUndeliverableException.CONVERSATION_GONE);
    }

    @Test
    void theApprovedTemplateCallsACustomerWithNoKnownNameCliente() {
        FollowUpTemplate template = FollowUpTemplate.of(UUID.randomUUID(), "Pedido", "Hola {nombre}",
                new WhatsAppTemplate("seguimiento_pedido", "es_MX", true), NOW, NOW);
        FollowUp followUp = schedule(ConversationChannel.WHATSAPP, template, null);
        recipient(followUp, ConversationChannel.WHATSAPP, "  ", NOW.minus(Duration.ofDays(2)));
        when(customers.sendAsMember(any(), any(), any(), anyString(), any(), any())).thenReturn(UUID.randomUUID());

        service.sendDue(WORKSPACE);

        ArgumentCaptor<ConversationReply.Template> sent = ArgumentCaptor.forClass(ConversationReply.Template.class);
        verify(customers).sendAsMember(any(), any(), any(), eq("Hola"), sent.capture(), any());
        assertThat(sent.getValue().parameters()).containsExactly("cliente");
        assertThat(followUp.status()).isEqualTo(FollowUpStatus.SENT);
    }

    @Test
    void aSentFollowUpTheChannelDidNotDeliverShowsAsFailed() {
        FollowUp followUp = schedule(ConversationChannel.WHATSAPP, null, "¿Te llegó tu pedido?");
        recipient(followUp, ConversationChannel.WHATSAPP, "Lucía", NOW);
        UUID message = UUID.randomUUID();
        when(customers.sendAsMember(any(), any(), any(), anyString(), any(), any())).thenReturn(message);
        service.sendDue(WORKSPACE);

        // A message that is not a follow-up changes nothing
        service.notDelivered(UUID.randomUUID(), ReplyNotDelivered.Reason.REFUSED);
        assertThat(followUp.status()).isEqualTo(FollowUpStatus.SENT);

        service.notDelivered(message, ReplyNotDelivered.Reason.CHANNEL_UNAVAILABLE);
        assertThat(followUp.status()).isEqualTo(FollowUpStatus.FAILED);
        assertThat(followUp.failure()).isEqualTo(FollowUpUndeliverableException.CHANNEL_UNAVAILABLE);
    }

    private FollowUp schedule(ConversationChannel channel, FollowUpTemplate template, String text) {
        FollowUp followUp = FollowUp.schedule(UUID.randomUUID(), UUID.randomUUID(), channel, template, text, NOW,
                UUID.randomUUID(), "Ana", NOW);
        followUps.saved.add(followUp);
        return followUp;
    }

    private void recipient(FollowUp followUp, ConversationChannel channel, String name, Instant lastMessageAt) {
        when(customers.recipientOf(followUp.conversationId())).thenReturn(Optional.of(new Recipient(
                followUp.conversationId(), channel, name, lastMessageAt)));
    }

    private static final class InMemoryFollowUps implements FollowUpRepository {

        private final List<FollowUp> saved = new ArrayList<>();

        @Override
        public Optional<FollowUp> claimDue(Instant now) {
            return saved.stream().filter(followUp -> followUp.isDue(now)).min(Comparator.comparing(FollowUp::dueAt));
        }

        @Override
        public Optional<FollowUp> findForUpdate(UUID id) {
            return saved.stream().filter(followUp -> followUp.id().equals(id)).findFirst();
        }

        @Override
        public Optional<FollowUp> findByMessageForUpdate(UUID messageId) {
            return saved.stream().filter(followUp -> messageId.equals(followUp.messageId())).findFirst();
        }

        @Override
        public void update(FollowUp followUp) {
        }

        @Override
        public List<FollowUpTemplate> templates() {
            return List.of();
        }

        @Override
        public Optional<FollowUpTemplate> findTemplate(UUID id) {
            return Optional.empty();
        }

        @Override
        public boolean saveTemplate(FollowUpTemplate template) {
            return true;
        }

        @Override
        public boolean deleteTemplate(UUID id) {
            return false;
        }

        @Override
        public void insert(FollowUpRow row) {
        }

        @Override
        public List<UUID> workspacesWithDue(Instant now) {
            return List.of(WORKSPACE);
        }

        @Override
        public List<FollowUpRow> ofConversation(UUID conversationId) {
            return List.of();
        }

        @Override
        public FollowUpPage page(FollowUpStatus status, int page, int size) {
            return new FollowUpPage(List.of(), 0);
        }
    }
}
