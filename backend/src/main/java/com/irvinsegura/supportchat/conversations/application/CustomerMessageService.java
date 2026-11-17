package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.conversations.CustomerMessages;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationNotFoundException;
import com.irvinsegura.supportchat.conversations.domain.model.CustomerProfile;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.CustomerRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerMessageService implements CustomerMessages {

    private final ConversationRepository conversations;
    private final CustomerRepository customers;
    private final MessageRepository messages;
    private final LiveUpdates live;

    CustomerMessageService(ConversationRepository conversations, CustomerRepository customers,
            MessageRepository messages, LiveUpdates live) {
        this.conversations = conversations;
        this.customers = customers;
        this.messages = messages;
        this.live = live;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Contact> contactOf(UUID conversationId) {
        return conversations.findById(conversationId).map(conversation -> new Contact(conversation.id(),
                conversation.channel(), conversation.contactId(), conversation.contactName()));
    }

    @Override
    @Transactional
    public Optional<Recipient> recipientOf(UUID conversationId) {
        return conversations.findById(conversationId).map(original -> {
            Conversation target = conversations.findOpenByContactForUpdate(original.channel(), original.contactId())
                    .orElse(original);
            return new Recipient(target.id(), target.channel(), customerName(target),
                    messages.lastFromContact(target.channel(), target.contactId()).orElse(null));
        });
    }

    @Override
    @Transactional
    public UUID sendAsMember(UUID conversationId, UUID memberId, String memberName, String text,
            ConversationReply.Template template, Instant at) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(ConversationNotFoundException::new);
        String reply = cut(text, Message.MAX_REPLY_LENGTH);
        Message message = Message.agentReply(Ids.newId(), conversation.id(), memberId, memberName, reply, at);
        messages.insert(message);
        live.messageAdded(conversation, message, null, template);
        return message.id();
    }

    // Never between the two halves of an emoji, which would leave a broken character at the end
    private static String cut(String text, int length) {
        if (text.length() <= length) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(length - 1)) ? length - 1 : length;
        return text.substring(0, end);
    }

    private String customerName(Conversation conversation) {
        if (conversation.channel() == ConversationChannel.WEB) {
            return null;
        }
        return customers.findProfile(conversation.channel(), conversation.contactId())
                .map(CustomerProfile::displayName)
                .orElse(conversation.contactName());
    }
}
