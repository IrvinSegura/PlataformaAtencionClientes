package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.conversations.ChannelConversations;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.application.AnswerService.PreparedQuestion;
import com.irvinsegura.supportchat.conversations.application.ConversationMetrics.HandoffReason;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.SystemNotice;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Serves customers who write from a messaging channel. A channel has no "new conversation" button, so each contact
 * has at most one open conversation per channel: their messages join it, and a new one starts after a long silence or
 * once the old one was closed. Asking for a person is a short message such as "quiero hablar con una persona", since
 * there is no button for it either; a customer who used up the assistant's questions is handed to the team too.
 */
@Service
class ChannelConversationService implements ChannelConversations {

    private static final Logger log = LoggerFactory.getLogger(ChannelConversationService.class);
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> PERSON_WORDS = Set.of(
            "persona", "humano", "humana", "agente", "asesor", "asesora", "operador", "operadora", "ejecutivo",
            "human", "person", "agent", "representative", "pessoa", "atendente", "personne", "humain", "conseiller",
            "conseillere");
    // A request for a person is short; a longer message mentioning an agent is a question for the assistant
    private static final int MAX_HANDOFF_WORDS = 8;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final AnswerService answers;
    private final ActionResponseService actionResponses;
    private final LiveUpdates live;
    private final ConversationSettings settings;
    private final ConversationMetrics metrics;
    private final AttentionService attention;
    private final TransactionTemplate transactions;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    ChannelConversationService(ConversationRepository conversations, MessageRepository messages,
            AnswerService answers, ActionResponseService actionResponses, LiveUpdates live,
            ConversationSettings settings, ConversationMetrics metrics, AttentionService attention,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.attention = attention;
        this.conversations = conversations;
        this.messages = messages;
        this.answers = answers;
        this.actionResponses = actionResponses;
        this.live = live;
        this.settings = settings;
        this.metrics = metrics;
        this.transactions = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    @Override
    public void receive(InboundMessage message) {
        if (message.channel() == ConversationChannel.WEB) {
            throw new IllegalArgumentException("Web visitors talk through the widget API");
        }
        String text = message.text() == null ? "" : message.text().strip();
        if (text.isEmpty()) {
            return;
        }
        // A channel cannot show a validation error: an overlong message is answered from its beginning
        String question = text.substring(0, Math.min(text.length(),
                Math.min(settings.maxQuestionChars(), Message.MAX_QUESTION_LENGTH)));
        PreparedQuestion prepared = transactions.execute(status -> {
            Instant now = clock.instant();
            Conversation conversation = openConversation(message, now);
            // A customer cannot be told to reload the page: past the limit, the team takes over instead
            if (conversation.usedUpQuestions(settings.maxQuestions())) {
                handOff(conversation, HandoffReason.QUESTION_LIMIT, now);
            }
            PreparedQuestion accepted = answers.accept(conversation, question, now);
            if (!accepted.forwarded() && asksForPerson(question)) {
                if (handOff(conversation, HandoffReason.ASKED, now)) {
                    attention.askedForPerson(conversation, question, now);
                }
                return null;
            }
            return accepted;
        });
        if (prepared != null && !prepared.forwarded()) {
            answers.answer(prepared, AnswerSink.NONE, message.model());
        }
    }

    @Override
    public void respondToAction(ConversationChannel channel, String contactId, UUID runId, boolean confirmed) {
        Optional<Conversation> conversation = readOnly.execute(status -> conversations.findOpenByContact(channel,
                contactId));
        if (conversation.isEmpty()) {
            log.info("Ignored an action response from a {} contact with no open conversation in workspace {}",
                    channel, TenantContext.requireWorkspaceId());
            return;
        }
        actionResponses.respond(conversation.get(), runId, confirmed);
    }

    private boolean handOff(Conversation conversation, HandoffReason reason, Instant now) {
        if (conversation.requestHandoff(now)) {
            Message notice = Message.notice(Ids.newId(), conversation.id(), SystemNotice.HANDOFF_REQUESTED, null,
                    null, now);
            conversations.update(conversation);
            messages.insert(notice);
            live.messageAdded(conversation, notice);
            live.conversationChanged(conversation);
            metrics.handedOff(conversation.channel(), reason);
            return true;
        }
        return false;
    }

    private Conversation openConversation(InboundMessage message, Instant now) {
        Optional<Conversation> open = conversations.findOpenByContactForUpdate(message.channel(),
                message.contactId());
        if (open.isPresent()) {
            Conversation current = open.get();
            if (!current.shouldStartOver(settings.visitorTokenTtl(), now)) {
                return current;
            }
            current.retire(now);
            conversations.update(current);
            live.conversationChanged(current);
        }
        Conversation started = Conversation.startOnChannel(Ids.newId(), TenantContext.requireWorkspaceId(),
                message.channel(), message.contactId(), message.contactName(), now);
        if (conversations.insertIfNoneOpen(started)) {
            metrics.started(started.channel());
            return started;
        }
        // Another message of the same contact started it a moment ago; that insert has committed by now
        return conversations.findOpenByContactForUpdate(message.channel(), message.contactId())
                .orElseThrow(() -> new IllegalStateException("The contact's open conversation vanished"));
    }

    static boolean asksForPerson(String text) {
        String folded = MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                .replaceAll("");
        String[] words = NON_WORD.split(folded.strip());
        if (words.length > MAX_HANDOFF_WORDS) {
            return false;
        }
        for (String word : words) {
            if (PERSON_WORDS.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
