package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.actions.ActionDefinition;
import com.irvinsegura.supportchat.actions.AssistantActions;
import com.irvinsegura.supportchat.billing.UsageAllowance;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.conversations.ConversationChannel;
import com.irvinsegura.supportchat.conversations.ModelPreference;
import com.irvinsegura.supportchat.conversations.application.AnswerLearning.Learned;
import com.irvinsegura.supportchat.conversations.application.ConversationSettings.Replies;
import com.irvinsegura.supportchat.conversations.application.ConversationViews.PendingActionView;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.Citation;
import com.irvinsegura.supportchat.conversations.domain.model.Citations;
import com.irvinsegura.supportchat.conversations.domain.model.Conversation;
import com.irvinsegura.supportchat.conversations.domain.model.GroundedPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.IntentDetector;
import com.irvinsegura.supportchat.conversations.domain.model.InvalidConversationTokenException;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.Message.ModelUsage;
import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter;
import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter.Routing;
import com.irvinsegura.supportchat.conversations.domain.model.ModelSelection;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.model.ReusableAnswer;
import com.irvinsegura.supportchat.conversations.domain.model.SmallTalk;
import com.irvinsegura.supportchat.conversations.domain.model.Source;
import com.irvinsegura.supportchat.conversations.domain.model.SystemNotice;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel.Completion;
import com.irvinsegura.supportchat.conversations.domain.port.ConversationRepository;
import com.irvinsegura.supportchat.conversations.domain.port.MessageRepository;
import com.irvinsegura.supportchat.knowledge.KnowledgeMatch;
import com.irvinsegura.supportchat.knowledge.KnowledgeSearch;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.workspaces.AssistantProfile;
import com.irvinsegura.supportchat.workspaces.DemoWorkspace;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Answers a customer's question: retrieve, select the relevant sources, choose a model, stream a grounded answer
 * (running the workspace's actions the model asks for), record it and its cost.
 * <p>
 * Split in two so failures reach the visitor the right way: {@link #prepare} validates and records the question
 * before anything is streamed, so its errors are ordinary HTTP errors; {@link #answer} then streams, and its failures
 * become a polite message inside the stream. No transaction stays open while the model writes or an action runs.
 * Writing one answer is {@link AnswerAttempt}'s, saving it {@link AnswerRecorder}'s and learning from it
 * {@link AnswerLearning}'s.
 */
@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final KnowledgeSearch knowledge;
    private final Workspaces workspaces;
    private final ChatModel chatModel;
    private final AssistantActions actions;
    private final UsageAllowance allowance;
    private final ConversationSettings settings;
    private final ModelChoices modelChoices;
    private final TransactionTemplate transactions;
    private final LiveUpdates live;
    private final AttentionService attention;
    private final AnswerReuse reuse;
    private final AnswerLearning learning;
    private final AnswerRecorder recorder;
    private final CustomerService customers;
    private final DemoWorkspace demo;
    private final Clock clock;

    AnswerService(ConversationRepository conversations, MessageRepository messages, KnowledgeSearch knowledge,
            Workspaces workspaces, ChatModel chatModel, AssistantActions actions, UsageAllowance allowance,
            ConversationSettings settings, ModelChoices modelChoices, TransactionTemplate transactions,
            LiveUpdates live, AttentionService attention, AnswerReuse reuse, AnswerLearning learning,
            AnswerRecorder recorder, CustomerService customers, DemoWorkspace demo, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.knowledge = knowledge;
        this.workspaces = workspaces;
        this.chatModel = chatModel;
        this.actions = actions;
        this.allowance = allowance;
        this.settings = settings;
        this.modelChoices = modelChoices;
        this.transactions = transactions;
        this.live = live;
        this.attention = attention;
        this.reuse = reuse;
        this.learning = learning;
        this.recorder = recorder;
        this.customers = customers;
        this.demo = demo;
        this.clock = clock;
    }

    /**
     * Authenticates the visitor, enforces the conversation's limits and records the question.
     */
    public PreparedQuestion prepare(UUID conversationId, String visitorToken, String question) {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            Conversation conversation = conversations.findByIdForUpdate(conversationId)
                    .orElseThrow(InvalidConversationTokenException::new);
            conversation.authenticate(visitorToken, settings.visitorTokenTtl(), now);
            return accept(conversation, question, now);
        });
    }

    /**
     * Records a question of a conversation the caller already locked and authenticated, e.g. a WhatsApp contact's.
     * Must run inside the caller's transaction.
     */
    PreparedQuestion accept(Conversation conversation, String question, Instant now) {
        Message asked = Message.question(Ids.newId(), conversation.id(), question, settings.maxQuestionChars(), now);
        // The model follows the customer's language by itself; this keeps the platform's own texts in step with it
        conversation.speak(Language.detect(question).orElse(null));
        // Saved with the question by the update below; a message for the team says as much about the topic
        conversation.tagIntents(IntentDetector.of(question));
        if (conversation.isAnsweredByAssistant() && !allowance.allows(UsageMetric.ANSWERS)) {
            // The plan's answers for the month are used up: the team answers instead, so nobody is left unanswered
            pauseAssistant(conversation, now);
        }
        // Checked before accepting: only questions for the assistant count towards the limit
        boolean forAssistant = conversation.isAnsweredByAssistant();
        conversation.acceptQuestion(settings.maxQuestions(), now);
        conversations.update(conversation);
        messages.insert(asked);
        live.messageAdded(conversation, asked);
        // After the question is saved, so the team reads it before the notice that it now waits for them
        boolean handedOff = attention.screen(conversation, asked.content(), now);
        return new PreparedQuestion(conversation.id(), conversation.workspaceId(), asked.id(), asked.content(), now,
                !forAssistant || handedOff, conversation.channel(), conversation.contactId(),
                settings.languageOf(conversation));
    }

    private void pauseAssistant(Conversation conversation, Instant now) {
        if (conversation.requestHandoff(now)) {
            Message notice = Message.notice(Ids.newId(), conversation.id(), SystemNotice.ASSISTANT_PAUSED, null, null,
                    now);
            conversations.update(conversation);
            messages.insert(notice);
            live.messageAdded(conversation, notice);
            live.conversationChanged(conversation);
        }
    }

    public void answer(PreparedQuestion question, AnswerSink sink) {
        answer(question, sink, ModelPreference.AUTOMATIC);
    }

    /**
     * @param preference the model the business chose for the question's channel
     */
    public void answer(PreparedQuestion question, AnswerSink sink, ModelPreference preference) {
        if (question.forwarded()) {
            throw new IllegalArgumentException("Forwarded messages are answered by the team, not the assistant");
        }
        List<Message> history = messages.recentBefore(question.conversationId(), question.messageId(),
                settings.historyMessages());
        Optional<SmallTalk.Kind> courtesy = SmallTalk.of(question.text())
                // "ok" to something the assistant asked is an answer to it, not a courtesy
                .filter(kind -> kind != SmallTalk.Kind.ACKNOWLEDGEMENT || !AnswerLearning.assistantAsked(history));
        if (courtesy.isPresent()) {
            // A greeting or a thanks needs no search and no model: a fixed reply says the same for free
            recorder.deliverFixed(question, sink, AnswerOutcome.SMALL_TALK, replies(question).to(courtesy.get()));
            return;
        }
        // The public demo answers on the platform's account: always with the economical model
        ModelPreference model = demo.is(question.workspaceId()) ? ModelPreference.FAST : preference;
        ModelSelection selection = modelChoices.selection(model);
        Learned learned = learning.before(question, history, selection);
        List<ActionDefinition> available = availableActions(question);
        AssistantProfile profile = workspaces.assistantProfile(question.workspaceId());
        String memory = customers.memoryOf(question.conversationId(), question.channel(), question.contactId());
        // An answer that may draw on what this customer said before is theirs alone, and a returning customer's
        // question may refer to it: neither is given again nor given from another customer's
        Optional<AnswerReuse.Key> reuseKey = memory.isEmpty()
                ? reuse.keyFor(question.text(), history, available, profile, model)
                : Optional.empty();
        Optional<ReusableAnswer> reusable = reuseKey.flatMap(reuse::find);
        if (reusable.isPresent()) {
            deliverReused(question, sink, reusable.get(), reuseKey.get());
            return;
        }
        List<Source> sources;
        try {
            sources = settings.sourceSelection().select(candidates(knowledge, settings, question.text()));
        } catch (RuntimeException e) {
            log.warn("Knowledge search failed for conversation {}", question.conversationId(), e);
            recorder.deliverFixed(question, sink, AnswerOutcome.FAILED, replies(question).failure());
            return;
        }
        if (sources.isEmpty() && available.isEmpty()) {
            // Nothing relevant and nothing to do: say so without paying for a model call that could only guess
            recorder.deliverFixed(question, sink, AnswerOutcome.NO_ANSWER, replies(question).noAnswer());
            return;
        }
        generate(question, new Context(history, profile, sources, available, reuseKey.orElse(null), learned, memory),
                sink, selection);
    }

    /**
     * What the model answers with: the earlier messages, the sources and actions it may use, and where to keep its
     * answer when it can be given again (null when it cannot).
     */
    private record Context(List<Message> history, AssistantProfile profile, List<Source> sources,
            List<ActionDefinition> available, AnswerReuse.Key reuseKey, Learned learned, String memory) {
    }

    private List<ActionDefinition> availableActions(PreparedQuestion question) {
        try {
            return actions.available();
        } catch (RuntimeException e) {
            // Answering from the knowledge base alone beats not answering
            log.warn("Could not load the actions for conversation {}", question.conversationId(), e);
            return List.of();
        }
    }

    private void generate(PreparedQuestion question, Context context, AnswerSink sink, ModelSelection selection) {
        List<Source> sources = context.sources();
        List<ActionDefinition> available = context.available();
        AssistantProfile profile = context.profile();
        ChatPrompt prompt = GroundedPrompt.build(profile.botName(), profile.workspaceName(), context.history(),
                question.text(), sources, available.stream().map(AnswerAttempt::toolSpec).toList(),
                context.memory());

        Routing routing = selection.select(question.text(), sources);
        // What was learned only ever moves a question up to the capable model, and only when the platform chooses
        if (context.learned() != null && routing.tier() == ModelTier.SIMPLE && selection.mayEscalate()) {
            routing = context.learned().routing();
        }
        AnswerAttempt attempt = new AnswerAttempt(chatModel, actions, settings.maxToolRounds(), question, sink,
                Ids.newId(), available);
        attempt.run(prompt, routing.tier());
        // The cheap model may miss an answer the sources do hold; one more try with the capable model is cheaper
        // than an unanswered customer. Only when nothing was shown or done yet, so the customer never sees two answers
        if (attempt.outcome() == AnswerOutcome.NO_ANSWER && attempt.canRetry() && settings.escalateNoAnswer()
                && selection.mayEscalate() && routing.tier() == ModelTier.SIMPLE && !sources.isEmpty()) {
            routing = new Routing(ModelTier.COMPLEX, ModelRouter.ESCALATED);
            attempt.run(prompt, ModelTier.COMPLEX);
        }

        String text = attempt.text();
        AnswerOutcome outcome = attempt.outcome();
        List<Citation> citations = Citations.citedIn(text, sources);
        boolean visitorGone = attempt.visitorGone();
        if (attempt.pending() != null && outcome != AnswerOutcome.FAILED && text.isEmpty()) {
            // The model proposed a change without a word: the buttons still need a line to go with them
            outcome = AnswerOutcome.ANSWERED;
            text = replies(question).confirmPrompt();
            visitorGone = !AnswerRecorder.trySend(sink, text);
        } else if (outcome == AnswerOutcome.NO_ANSWER) {
            text = replies(question).noAnswer();
            citations = List.of();
            visitorGone = !AnswerRecorder.trySend(sink, text);
        } else if (outcome == AnswerOutcome.FAILED && text.isEmpty()) {
            text = replies(question).failure();
            citations = List.of();
            if (!visitorGone) {
                visitorGone = !AnswerRecorder.trySend(sink, text);
            }
        }
        ModelUsage modelUsage = attempt.completions().isEmpty() ? null : new ModelUsage(
                attempt.completions().getLast().model(),
                attempt.completions().stream().mapToInt(Completion::inputTokens).sum(),
                attempt.completions().stream().mapToInt(Completion::outputTokens).sum(),
                recorder.elapsedMillis(question), attempt.tier(), routing.reason());
        PendingActionView pending = attempt.pending() == null || outcome == AnswerOutcome.FAILED ? null
                : PendingActionView.of(attempt.pending());
        UUID actionRunId = attempt.pending() != null ? attempt.pending().runId() : attempt.lastRunId();
        recorder.record(question, sink, attempt.answerId(), text, outcome, citations, modelUsage,
                attempt.completions(), actionRunId, pending, visitorGone);
        learning.after(question, context.learned(), routing, outcome, attempt.tier(), text);
        // Only a plain answer from the documents: one that used an action belongs to its customer
        if (context.reuseKey() != null && outcome == AnswerOutcome.ANSWERED && !attempt.usedActions()
                && attempt.pending() == null && actionRunId == null && !text.isEmpty() && modelUsage != null) {
            reuse.keep(question.workspaceId(), context.reuseKey(), text, citations, modelUsage.model(),
                    modelUsage.tier());
        }
    }

    private void deliverReused(PreparedQuestion question, AnswerSink sink, ReusableAnswer reused,
            AnswerReuse.Key key) {
        boolean visitorGone = !AnswerRecorder.trySend(sink, reused.text());
        ModelUsage modelUsage = new ModelUsage(reused.model(), 0, 0, recorder.elapsedMillis(question), reused.tier(),
                ModelRouter.REUSED);
        recorder.record(question, sink, Ids.newId(), reused.text(), AnswerOutcome.ANSWERED, reused.citations(),
                modelUsage, List.of(), null, null, visitorGone);
        reuse.markReused(key);
    }

    private Replies replies(PreparedQuestion question) {
        return settings.replies(question.language());
    }

    /**
     * Passages the knowledge search found for the question, before selection drops the weak ones.
     */
    static List<Source> candidates(KnowledgeSearch knowledge, ConversationSettings settings, String question) {
        // Twice the sources needed, so selection still has enough after dropping weak ones
        List<KnowledgeMatch> matches = knowledge.search(question,
                settings.sourceSelection().maxSources() * 2);
        return matches.stream()
                .map(match -> new Source(0, match.chunkId(), match.documentId(), match.documentTitle(),
                        match.content(), match.similarity(), match.keywordMatch()))
                .toList();
    }

    /**
     * A recorded question waiting for its answer.
     *
     * @param forwarded the customer asked for a person or is talking to one: the message waits for the team and the
     *                  assistant must not answer it
     * @param contactId the customer's identity on the channel, or null for web visitors
     * @param language  the language the platform words its own texts in for this question
     */
    public record PreparedQuestion(UUID conversationId, UUID workspaceId, UUID messageId, String text,
            Instant askedAt, boolean forwarded, ConversationChannel channel, String contactId, Language language) {
    }
}
