package com.irvinsegura.supportchat.conversations.domain.model;

import com.irvinsegura.supportchat.conversations.CustomerMessages;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One message of a conversation. Assistant messages also record how the question was handled, what they cite and
 * what they cost, which feeds the knowledge gaps report and usage billing.
 *
 * @param outcome    assistant messages only
 * @param usage      assistant messages that needed a model call only
 * @param authorId   agent replies: the member who wrote it; notices about an agent: that agent
 * @param authorName  the name of that member when the message was written
 * @param actionRunId assistant messages that ran, or asked to confirm, an action: that run
 */
public record Message(UUID id, UUID conversationId, MessageAuthor author, String content, AnswerOutcome outcome,
        List<Citation> citations, ModelUsage usage, UUID authorId, String authorName, Instant createdAt,
        UUID actionRunId) {

    public static final int MAX_QUESTION_LENGTH = 2_000;
    public static final int MAX_REPLY_LENGTH = CustomerMessages.MAX_TEXT_LENGTH;

    public Message {
        Objects.requireNonNull(id);
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(author);
        Objects.requireNonNull(content);
        Objects.requireNonNull(createdAt);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public Message(UUID id, UUID conversationId, MessageAuthor author, String content, AnswerOutcome outcome,
            List<Citation> citations, ModelUsage usage, UUID authorId, String authorName, Instant createdAt) {
        this(id, conversationId, author, content, outcome, citations, usage, authorId, authorName, createdAt, null);
    }

    public static Message question(UUID id, UUID conversationId, String text, int maxLength, Instant now) {
        String question = text == null ? "" : text.strip();
        if (question.isEmpty() || question.length() > Math.min(maxLength, MAX_QUESTION_LENGTH)) {
            throw new InvalidQuestionException(Math.min(maxLength, MAX_QUESTION_LENGTH));
        }
        return new Message(id, conversationId, MessageAuthor.VISITOR, question, null, List.of(), null, null, null,
                now);
    }

    public static Message answer(UUID id, UUID conversationId, String text, AnswerOutcome outcome,
            List<Citation> citations, ModelUsage usage, Instant now) {
        return answer(id, conversationId, text, outcome, citations, usage, null, now);
    }

    /**
     * @param actionRunId the action the answer ran or asks to confirm, or null
     */
    public static Message answer(UUID id, UUID conversationId, String text, AnswerOutcome outcome,
            List<Citation> citations, ModelUsage usage, UUID actionRunId, Instant now) {
        return new Message(id, conversationId, MessageAuthor.ASSISTANT, text, Objects.requireNonNull(outcome),
                citations, usage, null, null, now, actionRunId);
    }

    /**
     * What the assistant tells the customer once they confirmed or cancelled an action, without a model call.
     */
    public static Message actionResult(UUID id, UUID conversationId, String text, UUID actionRunId, Instant now) {
        return new Message(id, conversationId, MessageAuthor.ASSISTANT, text, AnswerOutcome.ANSWERED, List.of(), null,
                null, null, now, Objects.requireNonNull(actionRunId));
    }

    public static Message agentReply(UUID id, UUID conversationId, UUID agentId, String agentName, String text,
            Instant now) {
        String reply = text == null ? "" : text.strip();
        if (reply.isEmpty() || reply.length() > MAX_REPLY_LENGTH) {
            throw new InvalidReplyException(MAX_REPLY_LENGTH);
        }
        return new Message(id, conversationId, MessageAuthor.AGENT, reply, null, List.of(), null,
                Objects.requireNonNull(agentId), Objects.requireNonNull(agentName), now);
    }

    /**
     * @param agentId   the agent the notice is about, or null
     * @param agentName their name, or null
     */
    public static Message notice(UUID id, UUID conversationId, SystemNotice notice, UUID agentId, String agentName,
            Instant now) {
        return new Message(id, conversationId, MessageAuthor.SYSTEM, notice.name(), null, List.of(), null, agentId,
                agentName, now);
    }

    /**
     * @param model         the last model that answered; with tool rounds or an escalation there may be several calls
     * @param inputTokens   summed over every call made for the answer
     * @param latencyMillis from the question to the last word of the answer
     * @param tier          the tier of the last call, or null for answers older than model routing
     * @param routingReason why that tier was chosen, see {@link ModelRouter}
     */
    public record ModelUsage(String model, int inputTokens, int outputTokens, long latencyMillis,
            @Nullable ModelTier tier, @Nullable String routingReason) {

        public ModelUsage(String model, int inputTokens, int outputTokens, long latencyMillis) {
            this(model, inputTokens, outputTokens, latencyMillis, null, null);
        }
    }
}
