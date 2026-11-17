package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.actions.ActionDefinition;
import com.irvinsegura.supportchat.actions.AssistantActions;
import com.irvinsegura.supportchat.billing.UsageAllowance;
import com.irvinsegura.supportchat.billing.UsageMeter;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.conversations.ModelPreference;
import com.irvinsegura.supportchat.conversations.application.AnswerSink.VisitorGoneException;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerStreamGuard;
import com.irvinsegura.supportchat.conversations.domain.model.ChatModelUnavailableException;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ChatTurn;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolCall;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolResult;
import com.irvinsegura.supportchat.conversations.domain.model.Citation;
import com.irvinsegura.supportchat.conversations.domain.model.Citations;
import com.irvinsegura.supportchat.conversations.domain.model.GroundedPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.Message;
import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter;
import com.irvinsegura.supportchat.conversations.domain.model.ModelRouter.Routing;
import com.irvinsegura.supportchat.conversations.domain.model.ModelSelection;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.model.PlaygroundLimitReachedException;
import com.irvinsegura.supportchat.conversations.domain.model.Source;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel.Completion;
import com.irvinsegura.supportchat.knowledge.KnowledgeSearch;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.domain.Language;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import com.irvinsegura.supportchat.workspaces.AssistantProfile;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lets a business try its assistant before any customer sees it: the same retrieval, prompt and model as a real
 * answer, plus an inspector showing what produced it. Nothing is saved as a conversation and no knowledge gap is
 * opened, and the actions the model asks for are only shown, never run: a test must not cancel a real order.
 * <p>
 * Each answer still calls the language model, so it counts as an answer of the plan (under its own
 * {@value #USAGE_DIMENSION} dimension) and its tokens are recorded: the playground cannot become a free way around
 * the plan's limits.
 */
@Service
public class PlaygroundService {

    static final String USAGE_DIMENSION = "PLAYGROUND";
    static final String SIMULATED_RESULT = """
            Playground simulation: the action was not run, so its result is unknown. Answer as you would once it \
            returns, without inventing its result.""";
    private static final int MAX_HISTORY_TURNS = 10;

    private static final Logger log = LoggerFactory.getLogger(PlaygroundService.class);

    private final KnowledgeSearch knowledge;
    private final Workspaces workspaces;
    private final ChatModel chatModel;
    private final AssistantActions actions;
    private final ModelChoices modelChoices;
    private final ModelPrices prices;
    private final UsageMeter usage;
    private final UsageAllowance allowance;
    private final ConversationSettings settings;
    private final TransactionTemplate transactions;
    private final Clock clock;

    PlaygroundService(KnowledgeSearch knowledge, Workspaces workspaces, ChatModel chatModel,
            AssistantActions actions, ModelChoices modelChoices, ModelPrices prices, UsageMeter usage,
            UsageAllowance allowance, ConversationSettings settings, TransactionTemplate transactions, Clock clock) {
        this.knowledge = knowledge;
        this.workspaces = workspaces;
        this.chatModel = chatModel;
        this.actions = actions;
        this.modelChoices = modelChoices;
        this.prices = prices;
        this.usage = usage;
        this.allowance = allowance;
        this.settings = settings;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Checks the question and the plan before anything is streamed, so their errors are ordinary HTTP errors.
     */
    public PlaygroundQuestion prepare(String question, List<HistoryTurn> history, ModelPreference model) {
        Instant now = clock.instant();
        UUID conversationId = Ids.newId();
        Message asked = Message.question(Ids.newId(), conversationId, question, settings.maxQuestionChars(), now);
        Boolean allowed = transactions.execute(status -> allowance.allows(UsageMetric.ANSWERS));
        if (!Boolean.TRUE.equals(allowed)) {
            throw new PlaygroundLimitReachedException();
        }
        List<HistoryTurn> recent = history == null ? List.of()
                : history.subList(Math.max(0, history.size() - MAX_HISTORY_TURNS), history.size());
        List<Message> earlier = new ArrayList<>();
        for (HistoryTurn turn : recent) {
            if (turn.text() == null || turn.text().isBlank()) {
                continue;
            }
            earlier.add(turn.fromCustomer()
                    ? Message.question(Ids.newId(), conversationId, turn.text(), Message.MAX_QUESTION_LENGTH, now)
                    : Message.answer(Ids.newId(), conversationId, turn.text().strip(), AnswerOutcome.ANSWERED,
                            List.of(), null, now));
        }
        return new PlaygroundQuestion(TenantContext.requireWorkspaceId(), asked.content(), earlier,
                model == null ? ModelPreference.AUTOMATIC : model, now);
    }

    /**
     * Streams the answer through {@code onText} and returns what the inspector shows. Never throws for a model or
     * search failure: the result then says the answer failed, like a customer would see it.
     */
    public PlaygroundResult answer(PlaygroundQuestion question, Consumer<String> onText) {
        List<Source> candidates;
        try {
            candidates = AnswerService.candidates(knowledge, settings, question.text());
        } catch (RuntimeException e) {
            log.warn("Knowledge search failed in the playground of workspace {}", question.workspaceId(), e);
            return fixed(question, onText, AnswerOutcome.FAILED, List.of(), List.of());
        }
        List<Source> selected = settings.sourceSelection().select(candidates);
        List<ActionDefinition> available = availableActions();
        if (selected.isEmpty() && available.isEmpty()) {
            return fixed(question, onText, AnswerOutcome.NO_ANSWER, candidates, selected);
        }

        AssistantProfile profile = workspaces.assistantProfile(question.workspaceId());
        ChatPrompt prompt = GroundedPrompt.build(profile.botName(), profile.workspaceName(), question.history(),
                question.text(), selected, available.stream().map(AnswerAttempt::toolSpec).toList());
        ModelSelection selection = modelChoices.selection(question.model());
        Routing routing = selection.select(question.text(), selected);
        Map<String, ActionDefinition> byName = available.stream()
                .collect(Collectors.toMap(ActionDefinition::name, action -> action));

        Run run = new Run(byName, onText);
        try {
            run.generate(prompt, routing.tier());
            if (run.outcome == AnswerOutcome.NO_ANSWER && run.text.isEmpty() && run.toolCalls.isEmpty()
                    && settings.escalateNoAnswer() && selection.mayEscalate() && routing.tier() == ModelTier.SIMPLE
                    && !selected.isEmpty()) {
                routing = new Routing(ModelTier.COMPLEX, ModelRouter.ESCALATED);
                run.generate(prompt, ModelTier.COMPLEX);
            }
        } catch (VisitorGoneException e) {
            // Closing the tab must not make the model rounds already paid for free
            record(run.completions);
            throw e;
        }

        String text = run.text.toString().strip();
        List<Citation> citations = Citations.citedIn(text, selected);
        if (run.outcome == AnswerOutcome.NO_ANSWER) {
            text = replies(question).noAnswer();
            citations = List.of();
            onText.accept(text);
        } else if (run.outcome == AnswerOutcome.FAILED && text.isEmpty()) {
            text = replies(question).failure();
            onText.accept(text);
        }
        int inputTokens = run.completions.stream().mapToInt(Completion::inputTokens).sum();
        int outputTokens = run.completions.stream().mapToInt(Completion::outputTokens).sum();
        String model = run.completions.isEmpty() ? null : run.completions.getLast().model();
        BigDecimal cost = run.completions.stream()
                .map(completion -> prices.cost(completion.model(), completion.inputTokens(),
                        completion.outputTokens()))
                .reduce(Optional.of(BigDecimal.ZERO),
                        (sum, next) -> sum.flatMap(total -> next.map(total::add)))
                .orElse(null);
        record(run.completions);
        return new PlaygroundResult(run.outcome, text, citations, inspected(candidates, selected), run.toolCalls,
                new ModelReport(model, run.tier, routing.reason(), question.model(), inputTokens, outputTokens,
                        run.completions.isEmpty() ? null : cost, run.completions.size()),
                elapsedMillis(question));
    }

    private PlaygroundResult fixed(PlaygroundQuestion question, Consumer<String> onText, AnswerOutcome outcome,
            List<Source> candidates, List<Source> selected) {
        String text = outcome == AnswerOutcome.NO_ANSWER ? replies(question).noAnswer() : replies(question).failure();
        onText.accept(text);
        return new PlaygroundResult(outcome, text, List.of(), inspected(candidates, selected), List.of(),
                new ModelReport(null, null, null, question.model(), 0, 0, BigDecimal.ZERO, 0),
                elapsedMillis(question));
    }

    private void record(List<Completion> completions) {
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> {
            for (Completion completion : completions) {
                usage.record(now, UsageMetric.INPUT_TOKENS, completion.model(), completion.inputTokens());
                usage.record(now, UsageMetric.OUTPUT_TOKENS, completion.model(), completion.outputTokens());
            }
            if (!completions.isEmpty()) {
                usage.record(now, UsageMetric.ANSWERS, USAGE_DIMENSION, 1);
            }
        });
    }

    private List<ActionDefinition> availableActions() {
        try {
            return actions.available();
        } catch (RuntimeException e) {
            log.warn("Could not load the actions for the playground", e);
            return List.of();
        }
    }

    private ConversationSettings.Replies replies(PlaygroundQuestion question) {
        // Fixed texts follow the question's language, like in a customer's conversation
        return settings.replies(Language.detect(question.text()).orElse(null));
    }

    private long elapsedMillis(PlaygroundQuestion question) {
        return Math.max(0, Duration.between(question.askedAt(), clock.instant()).toMillis());
    }

    private static List<InspectedSource> inspected(List<Source> candidates, List<Source> selected) {
        Map<UUID, Integer> numbers = selected.stream()
                .collect(Collectors.toMap(Source::chunkId, Source::number, (first, second) -> first));
        Set<UUID> chosen = numbers.keySet();
        return candidates.stream()
                .map(source -> new InspectedSource(numbers.get(source.chunkId()), source.documentId(),
                        source.documentTitle(), source.content(), source.similarity(), source.keywordMatch(),
                        chosen.contains(source.chunkId())))
                .toList();
    }

    /**
     * One playground answer: the model's rounds and the actions it asked for. Lives for one answer.
     */
    private final class Run {

        private final Map<String, ActionDefinition> available;
        private final Consumer<String> onText;
        private final List<Completion> completions = new ArrayList<>();
        private final List<SimulatedAction> toolCalls = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private AnswerOutcome outcome;
        private ModelTier tier;

        Run(Map<String, ActionDefinition> available, Consumer<String> onText) {
            this.available = available;
            this.onText = onText;
        }

        void generate(ChatPrompt prompt, ModelTier tier) {
            this.tier = tier;
            List<ChatTurn> turns = new ArrayList<>(prompt.turns());
            try {
                for (int round = 0; ; round++) {
                    ChatPrompt current = new ChatPrompt(prompt.system(), turns, prompt.sources(), prompt.tools());
                    AnswerStreamGuard guard = new AnswerStreamGuard();
                    Completion completion = chatModel.stream(current, tier, delta -> show(guard.accept(delta)));
                    show(guard.finish());
                    completions.add(completion);
                    if (completion.toolCalls().isEmpty() || round >= settings.maxToolRounds()) {
                        outcome = guard.isNoAnswer() && text.isEmpty() && toolCalls.isEmpty()
                                ? AnswerOutcome.NO_ANSWER
                                : AnswerOutcome.ANSWERED;
                        return;
                    }
                    List<ToolResult> results = completion.toolCalls().stream().map(this::simulate).toList();
                    turns.add(new ChatTurn(ChatTurn.Role.ASSISTANT, guard.fullText(), completion.toolCalls(),
                            List.of(), completion.replay()));
                    turns.add(new ChatTurn(ChatTurn.Role.VISITOR, "", List.of(), results, null));
                }
            } catch (ChatModelUnavailableException e) {
                log.warn("Language model unavailable in the playground: {}", e.getMessage());
                outcome = AnswerOutcome.FAILED;
            } catch (VisitorGoneException e) {
                throw e;
            } catch (RuntimeException e) {
                log.error("Playground answer failed", e);
                outcome = AnswerOutcome.FAILED;
            }
        }

        // Text of a later round starts a new paragraph after what earlier rounds showed
        private void show(String visible) {
            if (visible.isEmpty()) {
                return;
            }
            if (newRound && !text.isEmpty()) {
                onText.accept("\n\n");
                text.append("\n\n");
            }
            newRound = false;
            onText.accept(visible);
            text.append(visible);
        }

        private boolean newRound;

        private ToolResult simulate(ToolCall call) {
            newRound = true;
            ActionDefinition action = available.get(call.name());
            toolCalls.add(new SimulatedAction(call.name(), action == null ? null : action.description(),
                    call.input(), action != null && action.requiresConfirmation(), action != null));
            return new ToolResult(call.id(), SIMULATED_RESULT, false);
        }
    }

    /**
     * A question ready to be answered in the playground.
     *
     * @param history earlier turns of the test conversation, oldest first
     */
    public record PlaygroundQuestion(UUID workspaceId, String text, List<Message> history, ModelPreference model,
            Instant askedAt) {
    }

    /**
     * @param fromCustomer true for what the tester wrote, false for what the assistant answered
     */
    public record HistoryTurn(boolean fromCustomer, String text) {
    }

    /**
     * What the inspector shows next to a playground answer.
     *
     * @param sources   every passage the search found, in rank order, marked when it was given to the model
     * @param toolCalls the actions the model asked for, which a real conversation would have run
     */
    public record PlaygroundResult(AnswerOutcome outcome, String text, List<Citation> citations,
            List<InspectedSource> sources, List<SimulatedAction> toolCalls, ModelReport model, long latencyMillis) {
    }

    /**
     * @param number     the [n] the model saw it as, or null when selection dropped it
     * @param similarity cosine similarity to the question, or null when it was only found by keyword
     */
    public record InspectedSource(Integer number, UUID documentId, String documentTitle, String content,
            Double similarity, boolean keywordMatch, boolean usedByModel) {
    }

    /**
     * @param description          what the business said the action is for, or null for an unknown action
     * @param requiresConfirmation a real customer would have had to confirm it first
     * @param known                false when the model named an action the workspace does not have
     */
    public record SimulatedAction(String name, String description, Map<String, Object> input,
            boolean requiresConfirmation, boolean known) {
    }

    /**
     * @param model            the exact model that answered last, or null when no model was called
     * @param reason           why that tier was chosen, one of the {@link ModelRouter} or
     *                         {@link ModelSelection.Fixed} reasons
     * @param estimatedCostUsd from the configured list prices; null when a model has no price
     * @param modelCalls       requests made to the model (several when it asked for actions or was escalated)
     */
    public record ModelReport(String model, ModelTier tier, String reason, ModelPreference preference,
            int inputTokens, int outputTokens, BigDecimal estimatedCostUsd, int modelCalls) {
    }
}
