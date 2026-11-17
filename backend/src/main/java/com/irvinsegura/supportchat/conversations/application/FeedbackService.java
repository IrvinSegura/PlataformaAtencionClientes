package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationRating;
import com.irvinsegura.supportchat.conversations.domain.model.DifficultQuestion.Reason;
import com.irvinsegura.supportchat.conversations.domain.model.InvalidConversationTokenException;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.MessageAuthor;
import com.irvinsegura.supportchat.conversations.domain.model.MessageNotFoundException;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.FeedbackRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What visitors say about the help they got: whether each answer helped, and a rating of the conversation. Either
 * can be given again to change it.
 */
@Service
public class FeedbackService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final FeedbackRepository feedback;
    private final ConversationSettings settings;
    private final ConversationMetrics metrics;
    private final AnswerReuse reuse;
    private final DifficultQuestionService difficult;
    private final Clock clock;

    FeedbackService(ConversationRepository conversations, MessageRepository messages, FeedbackRepository feedback,
            ConversationSettings settings, ConversationMetrics metrics, AnswerReuse reuse,
            DifficultQuestionService difficult, Clock clock) {
        this.reuse = reuse;
        this.difficult = difficult;
        this.conversations = conversations;
        this.messages = messages;
        this.feedback = feedback;
        this.settings = settings;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void judgeAnswer(UUID conversationId, String visitorToken, UUID messageId, boolean helpful) {
        Instant now = clock.instant();
        Conversation conversation = authenticated(conversationId, visitorToken, now);
        Message answer = messages.findById(messageId)
                .filter(message -> message.conversationId().equals(conversationId))
                // Only the assistant's answers are judged: the team's replies are rated with the conversation
                .filter(message -> message.author() == MessageAuthor.ASSISTANT)
                .orElseThrow(MessageNotFoundException::new);
        feedback.saveAnswerFeedback(conversation.workspaceId(), conversationId, answer.id(), helpful, now);
        metrics.answerJudged(helpful);
        if (!helpful) {
            // An answer that did not help is not given again to the next customer who asks the same
            reuse.forget(answer.content());
            if (answer.outcome() == AnswerOutcome.ANSWERED && answer.actionRunId() == null) {
                // A "no sé" is a knowledge gap already; an answer that did not help is a question to learn
                List<Message> exchange = new ArrayList<>(
                        messages.recentBefore(conversationId, answer.id(), settings.historyMessages()));
                exchange.add(answer);
                AnswerLearning.lastExchange(exchange).ifPresent(last -> difficult.record(conversation.workspaceId(),
                        last.question(), Reason.UNHELPFUL, conversationId, last.answer()));
            }
        }
    }

    @Transactional
    public void rate(UUID conversationId, String visitorToken, int score, String comment) {
        Instant now = clock.instant();
        Conversation conversation = authenticated(conversationId, visitorToken, now);
        feedback.saveRating(conversation.workspaceId(), new ConversationRating(conversationId, score, comment, now));
        metrics.rated(score);
    }

    private Conversation authenticated(UUID conversationId, String visitorToken, Instant now) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(InvalidConversationTokenException::new);
        conversation.authenticate(visitorToken, settings.visitorTokenTtl(), now);
        return conversation;
    }
}
