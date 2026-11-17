package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Business meters of the conversations. Tags only take bounded values (never a workspace, contact or text), so the
 * number of series stays small whatever the number of customers.
 */
@Component
class ConversationMetrics {

    /** Why a conversation went to the team. */
    enum HandoffReason {
        // The visitor pressed the widget's button
        VISITOR,
        // The customer wrote that they want a person
        ASKED,
        // The customer used up the assistant's questions
        QUESTION_LIMIT,
        // The customer seemed upset
        FRUSTRATED
    }

    private final MeterRegistry registry;
    private final DistributionSummary ratings;

    ConversationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ratings = DistributionSummary.builder("supportchat.feedback.ratings")
                .description("Scores customers gave their conversations, from 1 to 5")
                .register(registry);
    }

    void started(ConversationChannel channel) {
        registry.counter("supportchat.conversations.started", "channel", channel.name()).increment();
    }

    void handedOff(ConversationChannel channel, HandoffReason reason) {
        registry.counter("supportchat.conversations.handoffs", "channel", channel.name(),
                "reason", reason.name().toLowerCase(Locale.ROOT)).increment();
    }

    void answerJudged(boolean helpful) {
        registry.counter("supportchat.feedback.answers", "helpful", String.valueOf(helpful)).increment();
    }

    void rated(int score) {
        ratings.record(score);
    }
}
