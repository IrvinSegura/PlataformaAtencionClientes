package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.application.ConversationMetrics.HandoffReason;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.ConversationSummary;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.OperatorMessageView;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.VisitorConversationView;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.ConversationNotFoundException;
import com.irvinsegura.supportchat.conversations.domain.model.InvalidConversationTokenException;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.SystemNotice;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.identity.OperatorDirectory;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves a conversation between the assistant and a person. Each change locks the conversation row, records a notice
 * in the conversation and pushes it live, so the visitor and every operator see the same story in the same order.
 * <p>
 * Any member may take a conversation over. Owners and administrators may also release or close one that someone else
 * is answering, e.g. when that agent left for the day; only the agent who holds it may reply.
 */
@Service
public class HandoffService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final OperatorDirectory operators;
    private final LiveUpdates live;
    private final ConversationSettings settings;
    private final ConversationMetrics metrics;
    private final AttentionService attention;
    private final Clock clock;

    HandoffService(ConversationRepository conversations, MessageRepository messages, OperatorDirectory operators,
            LiveUpdates live, ConversationSettings settings, ConversationMetrics metrics, AttentionService attention,
            Clock clock) {
        this.attention = attention;
        this.conversations = conversations;
        this.messages = messages;
        this.operators = operators;
        this.live = live;
        this.settings = settings;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public VisitorConversationView visitorState(UUID conversationId, String visitorToken) {
        return VisitorConversationView.of(visitorConversation(conversationId, visitorToken, false));
    }

    @Transactional
    public VisitorConversationView requestHandoff(UUID conversationId, String visitorToken) {
        Conversation conversation = visitorConversation(conversationId, visitorToken, true);
        Instant now = clock.instant();
        if (conversation.requestHandoff(now)) {
            save(conversation, Message.notice(Ids.newId(), conversationId, SystemNotice.HANDOFF_REQUESTED, null,
                    null, now));
            metrics.handedOff(conversation.channel(), HandoffReason.VISITOR);
            attention.askedForPerson(conversation, null, now);
        }
        return VisitorConversationView.of(conversation);
    }

    @Transactional
    public VisitorConversationView cancelHandoff(UUID conversationId, String visitorToken) {
        Conversation conversation = visitorConversation(conversationId, visitorToken, true);
        Instant now = clock.instant();
        if (conversation.cancelHandoff(now)) {
            save(conversation, Message.notice(Ids.newId(), conversationId, SystemNotice.HANDOFF_CANCELLED, null,
                    null, now));
        }
        return VisitorConversationView.of(conversation);
    }

    @Transactional
    public ConversationSummary takeOver(UUID conversationId, UUID memberId) {
        Conversation conversation = operatorConversation(conversationId);
        String name = operators.displayNameOf(memberId);
        Instant now = clock.instant();
        if (conversation.takeOver(memberId, name, now)) {
            save(conversation, Message.notice(Ids.newId(), conversationId, SystemNotice.AGENT_JOINED, memberId,
                    name, now));
        }
        return ConversationSummary.of(conversation);
    }

    @Transactional
    public ConversationSummary release(UUID conversationId, UUID memberId, WorkspaceRole role) {
        Conversation conversation = operatorConversation(conversationId);
        UUID agentId = conversation.agentId();
        String agentName = conversation.agentName();
        Instant now = clock.instant();
        if (conversation.release(memberId, mayOverride(role), now)) {
            save(conversation, Message.notice(Ids.newId(), conversationId, SystemNotice.AGENT_LEFT, agentId,
                    agentName, now));
        }
        return ConversationSummary.of(conversation);
    }

    @Transactional
    public ConversationSummary close(UUID conversationId, UUID memberId, WorkspaceRole role) {
        Conversation conversation = operatorConversation(conversationId);
        Instant now = clock.instant();
        if (conversation.close(memberId, mayOverride(role), now)) {
            save(conversation, Message.notice(Ids.newId(), conversationId, SystemNotice.CONVERSATION_CLOSED, null,
                    null, now));
        }
        return ConversationSummary.of(conversation);
    }

    @Transactional
    public OperatorMessageView reply(UUID conversationId, UUID memberId, String text) {
        Conversation conversation = operatorConversation(conversationId);
        Instant now = clock.instant();
        conversation.acceptAgentReply(memberId, now);
        Message reply = Message.agentReply(Ids.newId(), conversationId, memberId, conversation.agentName(), text,
                now);
        conversations.update(conversation);
        messages.insert(reply);
        live.messageAdded(conversation, reply);
        return OperatorMessageView.of(reply);
    }

    private void save(Conversation conversation, Message notice) {
        conversations.update(conversation);
        messages.insert(notice);
        live.messageAdded(conversation, notice);
        live.conversationChanged(conversation);
    }

    private Conversation visitorConversation(UUID conversationId, String visitorToken, boolean forUpdate) {
        Conversation conversation = (forUpdate
                ? conversations.findByIdForUpdate(conversationId)
                : conversations.findById(conversationId))
                // Same answer as a wrong token: whether an id exists is none of an unauthenticated caller's business
                .orElseThrow(InvalidConversationTokenException::new);
        conversation.authenticate(visitorToken, settings.visitorTokenTtl(), clock.instant());
        return conversation;
    }

    private Conversation operatorConversation(UUID conversationId) {
        return conversations.findByIdForUpdate(conversationId).orElseThrow(ConversationNotFoundException::new);
    }

    private static boolean mayOverride(WorkspaceRole role) {
        return role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN;
    }
}
