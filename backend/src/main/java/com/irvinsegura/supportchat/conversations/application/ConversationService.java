package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.actions.ActionRunView;
import com.irvinsegura.supportchat.actions.AssistantActions;
import com.irvinsegura.supportchat.conversations.ConversationActivity;
import com.irvinsegura.supportchat.conversations.ConversationIntent;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationDetail;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationPageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationSummary;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.MessageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.OperatorMessageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.PendingActionView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.RatingView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.StartedConversation;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationHandling;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationNotFoundException;
import com.irvinsegura.supportchat.conversations.domain.model.InvalidConversationTokenException;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository.ConversationPage;
import com.irvinsegura.supportchat.conversations.domain.port.FeedbackRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.domain.SecretTokens;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService implements ConversationActivity {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConversationSettings settings;
    private final AssistantActions actions;
    private final FeedbackRepository feedback;
    private final ConversationMetrics metrics;
    private final LiveUpdates live;
    private final Clock clock;

    ConversationService(ConversationRepository conversations, MessageRepository messages,
            ConversationSettings settings, AssistantActions actions, FeedbackRepository feedback,
            ConversationMetrics metrics, LiveUpdates live, Clock clock) {
        this.live = live;
        this.conversations = conversations;
        this.messages = messages;
        this.settings = settings;
        this.actions = actions;
        this.feedback = feedback;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Starts a conversation for an anonymous visitor in the bound workspace.
     *
     * @param language the language the visitor's browser prefers, or null; their messages may change it
     */
    @Transactional
    public StartedConversation start(String origin, Language language) {
        Instant now = clock.instant();
        String token = SecretTokens.generate();
        Conversation conversation = Conversation.start(Ids.newId(), TenantContext.requireWorkspaceId(),
                SecretTokens.hash(token), origin, now);
        conversation.speak(language);
        conversations.insert(conversation);
        metrics.started(conversation.channel());
        return new StartedConversation(conversation.id(), token, now);
    }

    /**
     * The visitor's own conversation, e.g. to redraw the widget after a page reload.
     */
    @Transactional(readOnly = true)
    public List<MessageView> visitorHistory(UUID conversationId, String visitorToken) {
        Conversation conversation = conversations.findById(conversationId)
                // Same answer as a wrong token: whether an id exists is none of an unauthenticated caller's business
                .orElseThrow(InvalidConversationTokenException::new);
        Instant now = clock.instant();
        conversation.authenticate(visitorToken, settings.visitorTokenTtl(), now);
        // Confirmations still open survive a reload: the widget draws their buttons again
        Map<UUID, ActionRunView> pending = actions.runsOf(conversationId).stream()
                .filter(run -> run.awaitsConfirmation(now))
                .collect(Collectors.toMap(ActionRunView::id, Function.identity()));
        // The widget shows again which answers the visitor already judged
        Map<UUID, Boolean> verdicts = feedback.answerFeedback(conversationId);
        return messages.all(conversationId).stream()
                .map(message -> {
                    ActionRunView run = message.actionRunId() == null ? null : pending.get(message.actionRunId());
                    return MessageView.of(message, run == null ? null : PendingActionView.of(run),
                            verdicts.get(message.id()));
                })
                .toList();
    }

    /**
     * @param intent only conversations about this, or null for all of them
     */
    @Transactional(readOnly = true)
    public ConversationPageView page(ConversationHandling handling, ConversationIntent intent, int page, int size) {
        ConversationPage result = conversations.page(handling, intent, page, size);
        return new ConversationPageView(result.conversations().stream().map(ConversationSummary::of).toList(),
                page, size, result.total());
    }

    /**
     * The team sets what the conversation is about, correcting the automatic tags; later messages add none.
     */
    @Transactional
    public ConversationSummary setIntents(UUID conversationId, Set<ConversationIntent> intents) {
        Conversation conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(ConversationNotFoundException::new);
        conversation.setIntents(intents);
        conversations.update(conversation);
        live.conversationChanged(conversation);
        return ConversationSummary.of(conversation);
    }

    @Transactional(readOnly = true)
    public ConversationDetail detail(UUID conversationId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(ConversationNotFoundException::new);
        Map<UUID, Boolean> verdicts = feedback.answerFeedback(conversationId);
        return new ConversationDetail(ConversationSummary.of(conversation),
                messages.all(conversationId).stream()
                        .map(message -> OperatorMessageView.of(message, verdicts.get(message.id())))
                        .toList(),
                actions.runsOf(conversationId),
                feedback.rating(conversationId).map(RatingView::of).orElse(null));
    }

    // Joins the caller's transaction, bound to the workspace being looked at
    @Override
    @Transactional(readOnly = true)
    public Activity since(Instant since) {
        return conversations.activitySince(since);
    }
}
