package com.irvinsegura.supportchat.conversations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.irvinsegura.supportchat.conversations.CustomerMessages;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.CustomerRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerMessageServiceTest {

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final LiveUpdates live = mock(LiveUpdates.class);
    private final CustomerMessageService service = new CustomerMessageService(conversations,
            mock(CustomerRepository.class), messages, live);

    @Test
    void aTextLongerThanAMessageKeepsIsCutToTheLimit() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = mock(Conversation.class);
        when(conversation.id()).thenReturn(conversationId);
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation));
        // A template whose name placeholder, once filled, pushes the text past the limit
        String text = "a".repeat(CustomerMessages.MAX_TEXT_LENGTH) + " Lucía";

        UUID id = service.sendAsMember(conversationId, UUID.randomUUID(), "Ana", text, null, Instant.now());

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messages).insert(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(id);
        assertThat(saved.getValue().content()).hasSize(CustomerMessages.MAX_TEXT_LENGTH);
        verify(live).messageAdded(eq(conversation), eq(saved.getValue()), any(), any());
    }

    @Test
    void theCutNeverSplitsAnEmoji() {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = mock(Conversation.class);
        when(conversation.id()).thenReturn(conversationId);
        when(conversations.findById(conversationId)).thenReturn(Optional.of(conversation));
        // The emoji takes two chars and the limit falls between them
        String text = "a".repeat(CustomerMessages.MAX_TEXT_LENGTH - 1) + "😀 gracias";

        service.sendAsMember(conversationId, UUID.randomUUID(), "Ana", text, null, Instant.now());

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messages).insert(saved.capture());
        assertThat(saved.getValue().content()).isEqualTo("a".repeat(CustomerMessages.MAX_TEXT_LENGTH - 1));
    }
}
