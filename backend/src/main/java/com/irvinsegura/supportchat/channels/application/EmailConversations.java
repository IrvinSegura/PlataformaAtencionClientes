package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.ReplyNotDelivered.Reason;
import com.irvinsegura.supportchat.channels.application.ChannelMetrics.Delivery;
import com.irvinsegura.supportchat.channels.application.ChannelReplies.Format;
import com.irvinsegura.supportchat.channels.domain.model.DeliveryFailedException;
import com.irvinsegura.supportchat.channels.domain.model.EmailActionReply;
import com.irvinsegura.supportchat.channels.domain.model.EmailChannel;
import com.irvinsegura.supportchat.channels.domain.model.EmailThread;
import com.irvinsegura.supportchat.channels.domain.model.OutgoingEmail;
import com.irvinsegura.supportchat.channels.domain.port.EmailChannelRepository;
import com.irvinsegura.supportchat.channels.domain.port.EmailSender;
import com.irvinsegura.supportchat.conversations.ChannelConversations;
import com.irvinsegura.supportchat.conversations.ChannelConversations.InboundMessage;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.conversations.ConversationReply.PendingAction;
import com.irvinsegura.supportchat.shared.domain.DomainException;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Joins the mailbox with the conversations: answers each customer mail, and sends every reply meant for a customer
 * who wrote by mail, in the thread of their last message. Both run after the transaction that produced them commits,
 * off the request thread; the outbox delivers an event again if the process stops first or sending fails for a
 * reason that may pass.
 */
@Component
class EmailConversations {

    private static final Logger log = LoggerFactory.getLogger(EmailConversations.class);

    private final ChannelConversations conversations;
    private final EmailChannelRepository channels;
    private final EmailSender sender;
    private final ChannelMetrics metrics;
    private final UndeliveredReplies undelivered;
    private final TransactionTemplate transactions;

    EmailConversations(ChannelConversations conversations, EmailChannelRepository channels, EmailSender sender,
            ChannelMetrics metrics, UndeliveredReplies undelivered, PlatformTransactionManager transactionManager) {
        this.conversations = conversations;
        this.channels = channels;
        this.sender = sender;
        this.metrics = metrics;
        this.undelivered = undelivered;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Async
    @TransactionalEventListener
    void on(EmailReceived email) {
        metrics.received(ConversationChannel.EMAIL);
        TenantContext.runAs(email.workspaceId(), () -> {
            try {
                EmailActionReply answer = email.actionReply();
                if (answer != null) {
                    conversations.respondToAction(ConversationChannel.EMAIL, email.contactId(), answer.runId(),
                            answer.confirmed());
                } else {
                    conversations.receive(new InboundMessage(ConversationChannel.EMAIL, email.contactId(),
                            email.contactName(), email.text()));
                }
            } catch (DomainException e) {
                // e.g. an action confirmed twice: nothing to retry
                log.info("Mail to workspace {} not handled: {}", email.workspaceId(), e.getCode());
            }
        });
    }

    @Async
    @TransactionalEventListener
    void on(ConversationReply reply) {
        if (reply.channel() != ConversationChannel.EMAIL) {
            return;
        }
        TenantContext.runAs(reply.workspaceId(), () -> send(reply));
    }

    private void send(ConversationReply reply) {
        Optional<String> text = ChannelReplies.text(reply, Format.PLAIN);
        Optional<EmailChannel> channel = transactions.execute(status -> channels.find())
                .filter(EmailChannel::enabled);
        if (text.isEmpty()) {
            return;
        }
        if (channel.isEmpty()) {
            undelivered.report(reply, Reason.CHANNEL_UNAVAILABLE);
            return;
        }
        ChannelTexts texts = ChannelTexts.in(reply.language());
        PendingAction pending = reply.kind() == ConversationReply.Kind.ASSISTANT ? reply.pendingAction() : null;
        String body = pending == null ? text.get() : text.get() + "\n\n" + texts.replyToConfirm();
        String domain = channel.get().domain();
        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String messageId = pending == null ? "<" + UUID.randomUUID() + "@" + domain + ">"
                : EmailActionReply.messageIdFor(pending.runId(), nonce, domain);
        EmailThread thread = transactions.execute(status -> channels.findThread(reply.contactId()))
                .orElseGet(() -> new EmailThread(reply.contactId(), null, null, List.of()));
        OutgoingEmail email = new OutgoingEmail(reply.contactId(), thread.replySubject(texts.subject()), body,
                messageId, thread.lastMessageId(), thread.references(),
                reply.kind() != ConversationReply.Kind.AGENT);
        try {
            sender.send(channel.get(), email);
            metrics.delivery(ConversationChannel.EMAIL, Delivery.SENT);
        } catch (DeliveryFailedException.Permanent e) {
            metrics.delivery(ConversationChannel.EMAIL, Delivery.REFUSED);
            log.warn("The mail server refused a reply in conversation {}: {}", reply.conversationId(),
                    e.getMessage());
            undelivered.report(reply, Reason.REFUSED);
        } catch (RuntimeException e) {
            metrics.delivery(ConversationChannel.EMAIL, Delivery.RETRY);
            throw e;
        }
    }
}
