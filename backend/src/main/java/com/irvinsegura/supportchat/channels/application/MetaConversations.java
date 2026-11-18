package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.channels.ReplyNotDelivered.Reason;
import com.irvinsegura.supportchat.channels.application.ChannelMetrics.Delivery;
import com.irvinsegura.supportchat.channels.application.ChannelReplies.ButtonPress;
import com.irvinsegura.supportchat.channels.application.ChannelReplies.Format;
import com.irvinsegura.supportchat.channels.domain.model.DeliveryFailedException;
import com.irvinsegura.supportchat.channels.domain.model.MetaChannel;
import com.irvinsegura.supportchat.channels.domain.model.MetaPlatform;
import com.irvinsegura.supportchat.channels.domain.model.OutboundMessage;
import com.irvinsegura.supportchat.channels.domain.port.MetaChannelRepository;
import com.irvinsegura.supportchat.channels.domain.port.MetaGraph;
import com.irvinsegura.supportchat.conversations.ChannelConversations;
import com.irvinsegura.supportchat.conversations.ChannelConversations.InboundMessage;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.shared.domain.DomainException;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Joins Messenger and Instagram with the conversations: answers each accepted message, and sends every reply meant
 * for a customer there. Both run after the transaction that produced them commits, off the request thread; the
 * outbox delivers an event again if the process stops first or sending fails for a reason that may pass.
 */
@Component
class MetaConversations {

    private static final Logger log = LoggerFactory.getLogger(MetaConversations.class);

    private final ChannelConversations conversations;
    private final MetaChannelRepository channels;
    private final MetaGraph graph;
    private final ChannelMetrics metrics;
    private final UndeliveredReplies undelivered;
    private final TransactionTemplate transactions;

    MetaConversations(ChannelConversations conversations, MetaChannelRepository channels, MetaGraph graph,
            ChannelMetrics metrics, UndeliveredReplies undelivered, PlatformTransactionManager transactionManager) {
        this.conversations = conversations;
        this.channels = channels;
        this.graph = graph;
        this.metrics = metrics;
        this.undelivered = undelivered;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    static ConversationChannel channelOf(MetaPlatform platform) {
        return switch (platform) {
            case MESSENGER -> ConversationChannel.MESSENGER;
            case INSTAGRAM -> ConversationChannel.INSTAGRAM;
        };
    }

    private static Optional<MetaPlatform> platformOf(ConversationChannel channel) {
        return switch (channel) {
            case MESSENGER -> Optional.of(MetaPlatform.MESSENGER);
            case INSTAGRAM -> Optional.of(MetaPlatform.INSTAGRAM);
            default -> Optional.empty();
        };
    }

    @Async
    @TransactionalEventListener
    void on(MetaMessageReceived message) {
        ConversationChannel channel = channelOf(message.platform());
        metrics.received(channel);
        TenantContext.runAs(message.workspaceId(), () -> {
            try {
                Optional<ButtonPress> press = ChannelReplies.button(message.payload());
                if (press.isPresent()) {
                    conversations.respondToAction(channel, message.contactId(), press.get().runId(),
                            press.get().confirm());
                } else if (message.text() != null) {
                    conversations.receive(new InboundMessage(channel, message.contactId(),
                            contactName(message.platform(), message.contactId()), message.text()));
                }
            } catch (DomainException e) {
                // e.g. a button pressed twice: nothing to retry
                log.info("{} message of workspace {} not handled: {}", message.platform(), message.workspaceId(),
                        e.getCode());
            }
        });
    }

    private String contactName(MetaPlatform platform, String contactId) {
        return transactions.execute(status -> channels.find(platform))
                .flatMap(channel -> graph.contactName(channel, contactId))
                .orElse(null);
    }

    @Async
    @TransactionalEventListener
    void on(ConversationReply reply) {
        platformOf(reply.channel()).ifPresent(platform ->
                TenantContext.runAs(reply.workspaceId(), () -> send(platform, reply)));
    }

    private void send(MetaPlatform platform, ConversationReply reply) {
        Optional<OutboundMessage> message = ChannelReplies.compose(reply, Format.PLAIN);
        Optional<MetaChannel> channel = transactions.execute(status -> channels.find(platform))
                .filter(MetaChannel::enabled);
        if (message.isEmpty()) {
            return;
        }
        if (channel.isEmpty()) {
            undelivered.report(reply, Reason.CHANNEL_UNAVAILABLE);
            return;
        }
        try {
            graph.send(channel.get(), message.get());
            metrics.delivery(reply.channel(), Delivery.SENT);
        } catch (DeliveryFailedException.Permanent e) {
            metrics.delivery(reply.channel(), Delivery.REFUSED);
            log.warn("{} refused a reply in conversation {}: {}", platform, reply.conversationId(), e.getMessage());
            undelivered.report(reply, Reason.REFUSED);
        } catch (RuntimeException e) {
            metrics.delivery(reply.channel(), Delivery.RETRY);
            throw e;
        }
    }
}
