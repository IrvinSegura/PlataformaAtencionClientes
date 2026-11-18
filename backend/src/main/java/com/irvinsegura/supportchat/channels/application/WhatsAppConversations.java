package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.billing.UsageMeter;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.channels.ReplyNotDelivered.Reason;
import com.irvinsegura.supportchat.channels.application.ChannelMetrics.Delivery;
import com.irvinsegura.supportchat.channels.application.ChannelReplies.ButtonPress;
import com.irvinsegura.supportchat.channels.application.ChannelReplies.Format;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.model.TemplateMessage;
import com.irvinsegura.supportchat.channels.domain.model.WhatsAppChannel;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppChannelRepository;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppSender;
import com.irvinsegura.supportchat.channels.domain.port.WhatsAppSender.WhatsAppRejectedException;
import com.irvinsegura.supportchat.conversations.ChannelConversations;
import com.irvinsegura.supportchat.conversations.ChannelConversations.InboundMessage;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.shared.domain.DomainException;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Joins WhatsApp and the conversations: answers each accepted message, and sends every reply meant for a WhatsApp
 * contact. Both run after the transaction that produced them commits, off the request thread; the outbox delivers an
 * event again if the process stops first or sending fails for a reason that may pass.
 */
@Component
class WhatsAppConversations {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConversations.class);
    static final String DIRECTION_OUTBOUND = "outbound";

    private final ChannelConversations conversations;
    private final WhatsAppChannelRepository channels;
    private final WhatsAppSender sender;
    private final UsageMeter usage;
    private final ChannelMetrics metrics;
    private final UndeliveredReplies undelivered;
    private final TransactionTemplate transactions;
    private final Clock clock;

    WhatsAppConversations(ChannelConversations conversations, WhatsAppChannelRepository channels,
            WhatsAppSender sender, UsageMeter usage, ChannelMetrics metrics, UndeliveredReplies undelivered,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.conversations = conversations;
        this.channels = channels;
        this.sender = sender;
        this.usage = usage;
        this.metrics = metrics;
        this.undelivered = undelivered;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener
    void on(WhatsAppMessageReceived message) {
        metrics.received(ConversationChannel.WHATSAPP);
        TenantContext.runAs(message.workspaceId(), () -> {
            try {
                Optional<ButtonPress> press = ChannelReplies.button(message.buttonId());
                if (press.isPresent()) {
                    conversations.respondToAction(ConversationChannel.WHATSAPP, message.contactId(),
                            press.get().runId(), press.get().confirm());
                } else if (message.text() != null) {
                    conversations.receive(new InboundMessage(ConversationChannel.WHATSAPP, message.contactId(),
                            message.contactName(), message.text(), message.model()));
                }
            } catch (DomainException e) {
                // e.g. a button pressed twice: nothing to retry
                log.info("WhatsApp message of workspace {} not handled: {}", message.workspaceId(), e.getCode());
            }
        });
    }

    @Async
    @TransactionalEventListener
    void on(ConversationReply reply) {
        if (reply.channel() != ConversationChannel.WHATSAPP) {
            return;
        }
        TenantContext.runAs(reply.workspaceId(), () -> send(reply));
    }

    private void send(ConversationReply reply) {
        Optional<OutboundMessage> message = ChannelReplies.compose(reply, Format.WHATSAPP);
        Optional<WhatsAppChannel> channel = transactions.execute(status -> channels.find())
                .filter(WhatsAppChannel::enabled);
        if (message.isEmpty()) {
            return;
        }
        if (channel.isEmpty()) {
            undelivered.report(reply, Reason.CHANNEL_UNAVAILABLE);
            return;
        }
        try {
            ConversationReply.Template template = reply.template();
            if (template != null) {
                // After 24 hours of silence only an approved template reaches the customer
                sender.sendTemplate(channel.get(), new TemplateMessage(reply.contactId(), template.name(),
                        template.language(), template.parameters()));
            } else {
                sender.send(channel.get(), message.get());
            }
            metrics.delivery(ConversationChannel.WHATSAPP, Delivery.SENT);
        } catch (WhatsAppRejectedException e) {
            metrics.delivery(ConversationChannel.WHATSAPP, Delivery.REFUSED);
            log.warn("WhatsApp refused a reply in conversation {}: {}", reply.conversationId(), e.getMessage());
            undelivered.report(reply, Reason.REFUSED);
            return;
        } catch (RuntimeException e) {
            metrics.delivery(ConversationChannel.WHATSAPP, Delivery.RETRY);
            throw e;
        }
        transactions.executeWithoutResult(status ->
                usage.record(clock.instant(), UsageMetric.WHATSAPP_MESSAGES, DIRECTION_OUTBOUND, 1));
    }
}
