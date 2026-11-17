package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.actions.ActionOutcome;
import com.irvinsegura.supportchat.actions.ActionRunView;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationIntent;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.Citation;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationHandling;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationRating;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationStatus;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.MessageAuthor;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Read models of conversations, for visitors and operators.
 */
public final class ConversationViews {

    private ConversationViews() {
    }

    /**
     * @param visitorToken shown once, when the conversation starts; the server keeps only its hash
     */
    public record StartedConversation(UUID conversationId, String visitorToken, Instant startedAt) {
    }

    /**
     * @param authorName    the agent's name on agent replies and notices about an agent
     * @param pendingAction an action of this answer still waiting for the visitor's confirmation, or null
     * @param helpful       the visitor's verdict on this answer, or null when they gave none
     */
    public record MessageView(UUID id, MessageAuthor author, String content, @Nullable AnswerOutcome outcome,
            List<Citation> citations, @Nullable String authorName, Instant createdAt,
            @Nullable PendingActionView pendingAction, @Nullable Boolean helpful) {

        public static MessageView of(Message message) {
            return of(message, null, null);
        }

        public static MessageView of(Message message, PendingActionView pendingAction) {
            return of(message, pendingAction, null);
        }

        public static MessageView of(Message message, PendingActionView pendingAction, Boolean helpful) {
            return new MessageView(message.id(), message.author(), message.content(), message.outcome(),
                    message.citations(), message.authorName(), message.createdAt(), pendingAction, helpful);
        }
    }

    /**
     * A change the assistant proposed and the customer must confirm, e.g. a new delivery address.
     *
     * @param title     what people read, e.g. "Cambiar la dirección de entrega"
     * @param arguments exactly what will be sent, so the customer confirms what they see
     */
    public record PendingActionView(UUID runId, String title, Map<String, Object> arguments, Instant expiresAt) {

        public PendingActionView {
            arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        }

        static PendingActionView of(ActionOutcome outcome) {
            return new PendingActionView(outcome.runId(), outcome.title(), outcome.arguments(), outcome.expiresAt());
        }

        static PendingActionView of(ActionRunView run) {
            return new PendingActionView(run.id(), run.actionTitle(), run.arguments(), run.expiresAt());
        }
    }

    /**
     * What the visitor's widget needs to know about who is answering. The agent's id stays private.
     */
    public record VisitorConversationView(UUID conversationId, ConversationStatus status,
            ConversationHandling handling, @Nullable String agentName) {

        public static VisitorConversationView of(Conversation conversation) {
            return new VisitorConversationView(conversation.id(), conversation.status(), conversation.handling(),
                    conversation.agentName());
        }
    }

    /**
     * @param contactId     the customer's identity on the channel (their WhatsApp number); null on the web
     * @param contactName   the name the customer shows on that channel, or null
     * @param language      the customer's language as an ISO 639-1 code, e.g. "en"; null while unknown
     * @param intents       what the conversation is about, in a fixed order
     * @param intentsEdited whether the team set the topics by hand
     */
    public record ConversationSummary(UUID id, ConversationStatus status, int questionCount, @Nullable String origin,
            Instant startedAt, Instant lastActivityAt, ConversationHandling handling, @Nullable UUID agentId,
            @Nullable String agentName, @Nullable Instant handoffRequestedAt, ConversationChannel channel,
            @Nullable String contactId, @Nullable String contactName, @Nullable String language,
            @Nullable Instant frustratedAt, List<ConversationIntent> intents, boolean intentsEdited) {

        public static ConversationSummary of(Conversation conversation) {
            return new ConversationSummary(conversation.id(), conversation.status(), conversation.questionCount(),
                    conversation.origin(), conversation.startedAt(), conversation.lastActivityAt(),
                    conversation.handling(), conversation.agentId(), conversation.agentName(),
                    conversation.handoffRequestedAt(), conversation.channel(), conversation.contactId(),
                    conversation.contactName(),
                    conversation.language() == null ? null : conversation.language().code(),
                    conversation.frustratedAt(), conversation.intents().stream().sorted().toList(),
                    conversation.intentsEdited());
        }
    }

    public record ConversationPageView(List<ConversationSummary> items, int page, int size, long total) {
    }

    /**
     * What operators see; includes each answer's usage and the actions the assistant ran, which visitors never see.
     *
     * @param rating how the customer rated the conversation, or null
     */
    public record ConversationDetail(ConversationSummary conversation, List<OperatorMessageView> messages,
            List<ActionRunView> actionRuns, @Nullable RatingView rating) {

        public ConversationDetail {
            actionRuns = actionRuns == null ? List.of() : List.copyOf(actionRuns);
        }
    }

    /**
     * @param helpful the customer's verdict on this answer, or null when they gave none
     */
    public record OperatorMessageView(UUID id, MessageAuthor author, String content, @Nullable AnswerOutcome outcome,
            List<Citation> citations, Message.@Nullable ModelUsage usage, @Nullable UUID authorId,
            @Nullable String authorName, Instant createdAt, @Nullable UUID actionRunId, @Nullable Boolean helpful) {

        public static OperatorMessageView of(Message message) {
            return of(message, null);
        }

        public static OperatorMessageView of(Message message, Boolean helpful) {
            return new OperatorMessageView(message.id(), message.author(), message.content(), message.outcome(),
                    message.citations(), message.usage(), message.authorId(), message.authorName(),
                    message.createdAt(), message.actionRunId(), helpful);
        }
    }

    /**
     * @param score from 1 (very bad) to 5 (very good)
     */
    public record RatingView(int score, @Nullable String comment, Instant ratedAt) {

        public static RatingView of(ConversationRating rating) {
            return new RatingView(rating.score(), rating.comment(), rating.ratedAt());
        }
    }
}
