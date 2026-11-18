package com.irvinsegura.supportchat.channels.application;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Business meters of the channels: what customers send, what reaches them and whether the mailboxes can be read.
 * Tags only take bounded values, never a workspace or a contact.
 */
@Component
class ChannelMetrics {

    /** How sending a reply ended. */
    enum Delivery {
        SENT,
        // The provider refused it for good (blocked contact, closed 24 h window): it is not retried
        REFUSED,
        // It failed for a reason that may pass: the outbox tries again
        RETRY
    }

    private final MeterRegistry registry;

    ChannelMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void received(ConversationChannel channel) {
        registry.counter("supportchat.channel.messages.received", "channel", channel.name()).increment();
    }

    void delivery(ConversationChannel channel, Delivery delivery) {
        registry.counter("supportchat.channel.deliveries", "channel", channel.name(),
                "outcome", delivery.name().toLowerCase(Locale.ROOT)).increment();
    }

    void mailboxRead(boolean succeeded) {
        registry.counter("supportchat.channel.mailbox.reads", "outcome", succeeded ? "success" : "failure")
                .increment();
    }
}
