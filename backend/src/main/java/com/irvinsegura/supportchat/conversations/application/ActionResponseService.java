package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.actions.ActionOutcome;
import com.irvinsegura.supportchat.actions.AssistantActions;
import com.irvinsegura.supportchat.conversations.application.ConversationSettings.Replies;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.MessageView;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationClosedException;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationStatus;
import com.irvinsegura.supportchat.conversations.domain.model.InvalidConversationTokenException;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The customer confirmed or cancelled an action the assistant proposed. The action runs (or not) and the assistant
 * says how it went in a new message, without another model call: the business's own message when it sent one, a
 * fixed reply otherwise.
 */
@Service
public class ActionResponseService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final AssistantActions actions;
    private final LiveUpdates live;
    private final ConversationSettings settings;
    private final TransactionTemplate transactions;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    ActionResponseService(ConversationRepository conversations, MessageRepository messages, AssistantActions actions,
            LiveUpdates live, ConversationSettings settings, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.actions = actions;
        this.live = live;
        this.settings = settings;
        this.transactions = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    /**
     * A web visitor pressed a button of the widget.
     *
     * @param confirmed false cancels the action
     */
    public MessageView respondAsVisitor(UUID conversationId, String visitorToken, UUID runId, boolean confirmed) {
        Conversation conversation = readOnly.execute(status -> {
            Conversation found = conversations.findById(conversationId)
                    .orElseThrow(InvalidConversationTokenException::new);
            found.authenticate(visitorToken, settings.visitorTokenTtl(), clock.instant());
            return found;
        });
        return MessageView.of(respond(conversation, runId, confirmed));
    }

    /**
     * Runs or cancels the action and records the assistant's reply. Must run outside any transaction, since
     * confirming calls the business's system.
     */
    Message respond(Conversation conversation, UUID runId, boolean confirmed) {
        if (confirmed && conversation.status() == ConversationStatus.CLOSED) {
            throw new ConversationClosedException();
        }
        String channel = conversation.channel().name();
        ActionOutcome outcome = confirmed
                ? actions.confirm(runId, conversation.id(), channel, conversation.contactId())
                : actions.cancel(runId, conversation.id());
        Instant now = clock.instant();
        Message reply = Message.actionResult(Ids.newId(), conversation.id(),
                replyText(outcome, settings.replies(settings.languageOf(conversation))), runId, now);
        transactions.executeWithoutResult(status -> {
            messages.insert(reply);
            conversations.findById(conversation.id())
                    .ifPresent(current -> live.messageAdded(current, reply));
        });
        return reply;
    }

    private static String replyText(ActionOutcome outcome, Replies replies) {
        if (outcome.visitorMessage() != null && !outcome.visitorMessage().isBlank()) {
            return outcome.visitorMessage().strip();
        }
        return switch (outcome.status()) {
            case SUCCEEDED -> replies.done();
            case CANCELLED -> replies.cancelled();
            case EXPIRED -> replies.expired();
            case FAILED, REJECTED, RUNNING, PENDING_CONFIRMATION -> replies.failed();
        };
    }
}
