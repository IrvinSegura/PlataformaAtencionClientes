package com.irvinsegura.supportchat.conversations.application;

import com.irvinsegura.supportchat.actions.ActionDefinition;
import com.irvinsegura.supportchat.actions.ActionInvocation;
import com.irvinsegura.supportchat.actions.ActionOutcome;
import com.irvinsegura.supportchat.actions.ActionParameter;
import com.irvinsegura.supportchat.actions.AssistantActions;
import com.irvinsegura.supportchat.conversations.application.AnswerService.PreparedQuestion;
import com.irvinsegura.supportchat.conversations.application.AnswerSink.VisitorGoneException;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerOutcome;
import com.irvinsegura.supportchat.conversations.domain.model.AnswerStreamGuard;
import com.irvinsegura.supportchat.conversations.domain.model.ChatModelUnavailableException;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ChatTurn;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolCall;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolField;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolResult;
import com.irvinsegura.supportchat.conversations.domain.model.ChatPrompt.ToolSpec;
import com.irvinsegura.supportchat.conversations.domain.model.ModelTier;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel;
import com.irvinsegura.supportchat.conversations.domain.port.ChatModel.Completion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One answer being written: the model's rounds, the actions they ran and what reached the visitor. Not thread-safe;
 * lives for one answer.
 */
final class AnswerAttempt {

    private static final Logger log = LoggerFactory.getLogger(AnswerAttempt.class);
    static final String ONE_PENDING_ONLY = """
            Not run: another change is already waiting for the customer's confirmation. Ask them to confirm or \
            cancel that one first.""";
    static final String ACTION_ERROR = "The action could not be run because of an internal error.";

    private final ChatModel chatModel;
    private final AssistantActions actions;
    private final int maxToolRounds;
    private final PreparedQuestion question;
    private final AnswerSink sink;
    // Chosen before the model writes, so the actions it runs can point to it
    private final UUID answerId;
    private final Map<String, ActionDefinition> available;
    private final List<Completion> completions = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private AnswerOutcome outcome;
    private ModelTier tier;
    private ActionOutcome pending;
    private UUID lastRunId;
    private boolean usedActions;
    private boolean visitorGone;

    AnswerAttempt(ChatModel chatModel, AssistantActions actions, int maxToolRounds, PreparedQuestion question,
            AnswerSink sink, UUID answerId, List<ActionDefinition> available) {
        this.chatModel = chatModel;
        this.actions = actions;
        this.maxToolRounds = maxToolRounds;
        this.question = question;
        this.sink = sink;
        this.answerId = answerId;
        this.available = available.stream()
                .collect(Collectors.toMap(ActionDefinition::name, Function.identity()));
    }

    static ToolSpec toolSpec(ActionDefinition action) {
        String description = action.description()
                + (action.requiresConfirmation()
                        ? " This changes something: it only runs after the customer confirms it."
                        : "");
        List<ToolField> fields = action.parameters().stream()
                .map(AnswerAttempt::toolField)
                .toList();
        return new ToolSpec(action.name(), description, fields);
    }

    private static ToolField toolField(ActionParameter parameter) {
        return new ToolField(parameter.name(), parameter.type().name().toLowerCase(Locale.ROOT),
                parameter.description(), parameter.required(), parameter.pattern());
    }

    UUID answerId() {
        return answerId;
    }

    String text() {
        return text.toString().strip();
    }

    AnswerOutcome outcome() {
        return outcome;
    }

    ModelTier tier() {
        return tier;
    }

    List<Completion> completions() {
        return List.copyOf(completions);
    }

    /**
     * The change the model proposed that waits for the customer's confirmation, or null.
     */
    ActionOutcome pending() {
        return pending;
    }

    UUID lastRunId() {
        return lastRunId;
    }

    boolean usedActions() {
        return usedActions;
    }

    boolean visitorGone() {
        return visitorGone;
    }

    boolean canRetry() {
        return text.isEmpty() && !usedActions && !visitorGone;
    }

    void run(ChatPrompt prompt, ModelTier tier) {
        this.tier = tier;
        List<ChatTurn> turns = new ArrayList<>(prompt.turns());
        try {
            for (int round = 0; ; round++) {
                ChatPrompt current = new ChatPrompt(prompt.system(), turns, prompt.sources(), prompt.tools());
                AnswerStreamGuard guard = new AnswerStreamGuard();
                boolean[] separated = {text.isEmpty()};
                Completion completion = chatModel.stream(current, tier,
                        delta -> show(guard.accept(delta), separated));
                show(guard.finish(), separated);
                completions.add(completion);
                if (completion.toolCalls().isEmpty() || round >= maxToolRounds) {
                    if (!completion.toolCalls().isEmpty()) {
                        log.warn("The model kept asking for actions in conversation {}; answering with what it said",
                                question.conversationId());
                    }
                    // An answer that already said something after using actions is an answer
                    outcome = guard.isNoAnswer() && text.isEmpty() ? AnswerOutcome.NO_ANSWER : AnswerOutcome.ANSWERED;
                    return;
                }
                usedActions = true;
                List<ToolResult> results = completion.toolCalls().stream().map(this::use).toList();
                turns.add(new ChatTurn(ChatTurn.Role.ASSISTANT, guard.fullText(), completion.toolCalls(), List.of(),
                        completion.replay()));
                turns.add(new ChatTurn(ChatTurn.Role.VISITOR, "", List.of(), results, null));
            }
        } catch (VisitorGoneException e) {
            visitorGone = true;
            outcome = AnswerOutcome.FAILED;
        } catch (ChatModelUnavailableException e) {
            log.warn("Language model unavailable for conversation {}: {}", question.conversationId(), e.getMessage());
            outcome = AnswerOutcome.FAILED;
        } catch (RuntimeException e) {
            log.error("Answer generation failed for conversation {}", question.conversationId(), e);
            outcome = AnswerOutcome.FAILED;
        }
    }

    // Text of a later round starts a new paragraph after what earlier rounds showed
    private void show(String visible, boolean[] separated) {
        if (visible.isEmpty()) {
            return;
        }
        if (!separated[0]) {
            sink.text("\n\n");
            text.append("\n\n");
            separated[0] = true;
        }
        sink.text(visible);
        text.append(visible);
    }

    private ToolResult use(ToolCall call) {
        ActionDefinition action = available.get(call.name());
        if (action != null && action.requiresConfirmation() && pending != null) {
            // One button per answer: the customer confirms one change at a time
            return new ToolResult(call.id(), ONE_PENDING_ONLY, true);
        }
        try {
            ActionOutcome result = actions.invoke(new ActionInvocation(question.conversationId(), answerId,
                    question.channel().name(), question.contactId(), call.name(), call.input()));
            if (result.runId() != null) {
                lastRunId = result.runId();
            }
            if (result.isPendingConfirmation()) {
                pending = result;
            }
            return new ToolResult(call.id(), result.forModel(),
                    !result.succeeded() && !result.isPendingConfirmation());
        } catch (RuntimeException e) {
            log.error("Action {} failed in conversation {}", call.name(), question.conversationId(), e);
            return new ToolResult(call.id(), ACTION_ERROR, true);
        }
    }
}
