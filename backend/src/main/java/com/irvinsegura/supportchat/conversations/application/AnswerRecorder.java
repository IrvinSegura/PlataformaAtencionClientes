package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.billing.UsageMeter;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.conversations.application.AnswerService.PreparedQuestion;
import com.irvinsegura.supportchat.conversations.application.AnswerSink.VisitorGoneException;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.PendingActionView;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.Citation;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.Message.ModelUsage;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel.Completion;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.knowledge.QuestionUnanswered;
import com.irvinsegura.supportchat.shared.domain.Ids;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves an answer once it is written, with what it cost, tells the panel and the visitor, and counts it in the
 * metrics.
 */
@Component
class AnswerRecorder {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UsageMeter usage;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final LiveUpdates live;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    AnswerRecorder(ConversationRepository conversations, MessageRepository messages, UsageMeter usage,
            TransactionTemplate transactions, ApplicationEventPublisher events, LiveUpdates live,
            MeterRegistry meterRegistry, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.usage = usage;
        this.transactions = transactions;
        this.events = events;
        this.live = live;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /**
     * Sends a reply that needs no model, such as a greeting's or an apology, and saves it.
     */
    void deliverFixed(PreparedQuestion question, AnswerSink sink, AnswerOutcome outcome, String text) {
        boolean visitorGone = !trySend(sink, text);
        record(question, sink, Ids.newId(), text, outcome, List.of(), null, List.of(), null, null, visitorGone);
    }

    /**
     * @param completions the model calls the answer took, billed with it
     * @param pending     the change waiting for the customer's confirmation, or null
     * @param visitorGone the visitor left while it was written: it is saved but not completed on the stream
     */
    void record(PreparedQuestion question, AnswerSink sink, UUID answerId, String text, AnswerOutcome outcome,
            List<Citation> citations, ModelUsage modelUsage, List<Completion> completions, UUID actionRunId,
            PendingActionView pending, boolean visitorGone) {
        Instant now = clock.instant();
        Message answer = Message.answer(answerId, question.conversationId(), text.isEmpty() ? "…" : text,
                outcome, citations, modelUsage, actionRunId, now);
        transactions.executeWithoutResult(status -> {
            messages.insert(answer);
            conversations.findById(question.conversationId())
                    .ifPresent(conversation -> live.messageAdded(conversation, answer, pending));
            if (outcome == AnswerOutcome.NO_ANSWER) {
                events.publishEvent(new QuestionUnanswered(question.workspaceId(), question.conversationId(),
                        answer.id(), question.text(), question.askedAt()));
            }
            // Billed with the answer, so a usage report never counts an answer that was not saved
            for (Completion completion : completions) {
                usage.record(now, UsageMetric.INPUT_TOKENS, completion.model(), completion.inputTokens());
                usage.record(now, UsageMetric.OUTPUT_TOKENS, completion.model(), completion.outputTokens());
            }
            // A courtesy reply costs nothing, so it does not use up the plan's answers
            if (outcome == AnswerOutcome.ANSWERED || outcome == AnswerOutcome.NO_ANSWER) {
                usage.record(now, UsageMetric.ANSWERS, question.channel().name(), 1);
            }
        });
        recordMetrics(question, outcome, modelUsage);
        if (!visitorGone) {
            try {
                sink.completed(answer.id(), outcome, citations, pending);
            } catch (VisitorGoneException e) {
                // The answer is saved; the visitor will see it in the history when they come back
            }
        }
    }

    long elapsedMillis(PreparedQuestion question) {
        return Math.max(0, Duration.between(question.askedAt(), clock.instant()).toMillis());
    }

    /**
     * @return false when the visitor is gone
     */
    static boolean trySend(AnswerSink sink, String text) {
        try {
            sink.text(text);
            return true;
        } catch (VisitorGoneException e) {
            return false;
        }
    }

    private void recordMetrics(PreparedQuestion question, AnswerOutcome outcome, ModelUsage modelUsage) {
        Timer.builder("supportchat.answer.duration")
                .description("From the question to the end of the answer")
                .tag("outcome", outcome.name())
                .tag("channel", question.channel().name())
                .register(meterRegistry)
                .record(Duration.ofMillis(elapsedMillis(question)));
        if (modelUsage != null) {
            String tier = modelUsage.tier() == null ? "none" : modelUsage.tier().name();
            meterRegistry.counter("supportchat.llm.tokens", "type", "input", "tier", tier)
                    .increment(modelUsage.inputTokens());
            meterRegistry.counter("supportchat.llm.tokens", "type", "output", "tier", tier)
                    .increment(modelUsage.outputTokens());
        }
    }
}
