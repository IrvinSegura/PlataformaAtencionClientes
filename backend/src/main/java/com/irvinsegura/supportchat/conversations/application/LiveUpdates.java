package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ConversationReply;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationSummary;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.MessageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.OperatorMessageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.PendingActionView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.VisitorConversationView;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationEvent;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.MessageAuthor;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationEventBus;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pushes changes to the people watching a conversation: the operators of its workspace and the visitor, whichever
 * instance their stream is connected to.
 * <p>
 * Services announce a change inside the transaction that makes it, and the event bus carries it to every instance
 * once that transaction commits. Each instance then reads the current state, but only when someone connected to it
 * is watching, and hands the views to its {@link Listener}.
 * <p>
 * Customers on other channels (WhatsApp) are not watching a stream: what they must receive is published as a
 * {@link ConversationReply} for the channel's module to send once the transaction commits.
 */
@Service
public class LiveUpdates {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdates.class);

    private final ConversationEventBus bus;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final TransactionTemplate readOnly;
    private final ApplicationEventPublisher events;
    private final ConversationSettings settings;

    LiveUpdates(ConversationEventBus bus, ConversationRepository conversations, MessageRepository messages,
            PlatformTransactionManager transactionManager, ApplicationEventPublisher events,
            ConversationSettings settings) {
        this.bus = bus;
        this.events = events;
        this.settings = settings;
        this.conversations = conversations;
        this.messages = messages;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * Who answers the conversation, or whether it is open, changed.
     */
    public void conversationChanged(Conversation conversation) {
        bus.publish(ConversationEvent.conversationChanged(conversation.workspaceId(), conversation.id()));
    }

    public void messageAdded(Conversation conversation, Message message) {
        messageAdded(conversation, message, null);
    }

    /**
     * @param pendingAction an action the message asks the customer to confirm, or null
     */
    public void messageAdded(Conversation conversation, Message message, PendingActionView pendingAction) {
        messageAdded(conversation, message, pendingAction, null);
    }

    /**
     * @param template the approved WhatsApp template the message goes out as, or null to send its text
     */
    public void messageAdded(Conversation conversation, Message message, PendingActionView pendingAction,
            ConversationReply.Template template) {
        bus.publish(ConversationEvent.messageAdded(conversation.workspaceId(), conversation.id(), message.id()));
        if (conversation.channel() != ConversationChannel.WEB && message.author() != MessageAuthor.VISITOR) {
            events.publishEvent(reply(conversation, message, pendingAction, settings.languageOf(conversation),
                    template));
        }
    }

    private static ConversationReply reply(Conversation conversation, Message message,
            PendingActionView pendingAction, Language language, ConversationReply.Template template) {
        ConversationReply.Kind kind = switch (message.author()) {
            case ASSISTANT -> ConversationReply.Kind.ASSISTANT;
            case AGENT -> ConversationReply.Kind.AGENT;
            case SYSTEM, VISITOR -> ConversationReply.Kind.NOTICE;
        };
        return new ConversationReply(conversation.workspaceId(), conversation.id(), conversation.channel(),
                conversation.contactId(), message.id(), kind, message.content(), message.authorName(),
                message.citations().stream()
                        .map(citation -> "[" + citation.number() + "] " + citation.documentTitle())
                        .distinct()
                        .toList(),
                pendingAction == null ? null : new ConversationReply.PendingAction(pendingAction.runId(),
                        pendingAction.title(), pendingAction.arguments()),
                language, template);
    }

    /**
     * Starts handing every change to the listener, as views ready to send.
     */
    public void listen(Listener listener) {
        bus.subscribe(new ConversationEventBus.Subscriber() {
            @Override
            public void deliver(ConversationEvent event) {
                try {
                    dispatch(event, listener);
                } catch (RuntimeException e) {
                    // One event that cannot be read must not stop the others
                    log.warn("Could not push {} of conversation {}", event.type(), event.conversationId(), e);
                }
            }

            @Override
            public void eventsMissed() {
                listener.eventsMissed();
            }
        });
    }

    private void dispatch(ConversationEvent event, Listener listener) {
        UUID workspaceId = event.workspaceId();
        boolean operators = listener.watchesWorkspace(workspaceId);
        boolean visitor = listener.watchesConversation(event.conversationId());
        if (!operators && !visitor) {
            return;
        }
        switch (event.type()) {
            case CONVERSATION_CHANGED -> read(workspaceId, () -> conversations.findById(event.conversationId()))
                    .ifPresent(conversation -> listener.conversationChanged(workspaceId,
                            ConversationSummary.of(conversation), VisitorConversationView.of(conversation)));
            case MESSAGE_ADDED -> read(workspaceId, () -> conversations.findById(event.conversationId())
                    .flatMap(conversation -> messages.findById(event.messageId())
                            .map(message -> new Change(conversation, message))))
                    .ifPresent(change -> listener.messageAdded(workspaceId,
                            ConversationSummary.of(change.conversation()), OperatorMessageView.of(change.message()),
                            forVisitor(change.message())));
        }
    }

    /**
     * The visitor only sees agent replies and notices; their own messages and the assistant's answers already reach
     * them through the request that produced them.
     */
    private static MessageView forVisitor(Message message) {
        return message.author() == MessageAuthor.AGENT || message.author() == MessageAuthor.SYSTEM
                ? MessageView.of(message)
                : null;
    }

    private <T> Optional<T> read(UUID workspaceId, Supplier<Optional<T>> query) {
        return TenantContext.callAs(workspaceId, () -> readOnly.execute(status -> query.get()));
    }

    private record Change(Conversation conversation, Message message) {
    }

    /**
     * Receives the changes of every workspace; it says which ones it cares about, so nothing is read for changes
     * nobody connected here is watching.
     */
    public interface Listener {

        boolean watchesWorkspace(UUID workspaceId);

        boolean watchesConversation(UUID conversationId);

        void conversationChanged(UUID workspaceId, ConversationSummary summary, VisitorConversationView state);

        /**
         * @param forVisitor null when the visitor must not see the message
         */
        void messageAdded(UUID workspaceId, ConversationSummary summary, OperatorMessageView forOperators,
                MessageView forVisitor);

        /**
         * Changes may have been lost; whoever is watching should load the current state again.
         */
        void eventsMissed();
    }
}
