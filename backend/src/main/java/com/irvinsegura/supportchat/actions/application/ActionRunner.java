package com.irvinsegura.supportchat.actions.application;

import com.irvinsegura.supportchat.actions.ActionDefinition;
import com.irvinsegura.supportchat.actions.ActionInvocation;
import com.irvinsegura.supportchat.actions.ActionOutcome;
import com.irvinsegura.supportchat.actions.ActionParameter;
import com.irvinsegura.supportchat.actions.ActionRunStatus;
import com.irvinsegura.supportchat.actions.ActionRunView;
import com.irvinsegura.supportchat.actions.AssistantActions;
import com.irvinsegura.supportchat.actions.domain.model.Action;
import com.irvinsegura.supportchat.actions.domain.model.ActionArguments;
import com.irvinsegura.supportchat.actions.domain.model.ActionRun;
import com.irvinsegura.supportchat.actions.domain.model.ActionRunNotFoundException;
import com.irvinsegura.supportchat.actions.domain.model.Integration;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationAction;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationCall;
import com.irvinsegura.supportchat.actions.domain.model.InvalidActionArgumentsException;
import com.irvinsegura.supportchat.actions.domain.model.WebhookCall;
import com.irvinsegura.supportchat.actions.domain.model.WebhookResult;
import com.irvinsegura.supportchat.actions.domain.port.ActionRepository;
import com.irvinsegura.supportchat.actions.domain.port.ActionRunRepository;
import com.irvinsegura.supportchat.actions.domain.port.ActionWebhook;
import com.irvinsegura.supportchat.actions.domain.port.IntegrationGateway;
import com.irvinsegura.supportchat.actions.domain.port.IntegrationRepository;
import com.irvinsegura.supportchat.billing.UsageAllowance;
import com.irvinsegura.supportchat.billing.UsageMeter;
import com.irvinsegura.supportchat.billing.UsageMetric;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the actions the language model asks for: the workspace's own webhooks and the actions of its connected
 * integrations. Every request is recorded, including the ones never sent, so operators can see what the assistant
 * tried. Outside systems are called with no transaction open: a slow one must not hold a database connection.
 */
@Service
class ActionRunner implements AssistantActions {

    static final String PENDING_FOR_MODEL = """
            PENDING_CONFIRMATION: nothing has been done yet. The customer now sees a button to confirm or cancel \
            this action. In one or two sentences, tell them what will happen and that they must confirm it. Do not \
            say that it is done.""";

    static final String LIMIT_REACHED = "The plan's monthly limit of actions was reached";

    private final ActionRepository actions;
    private final ActionRunRepository runs;
    private final ActionService settings;
    private final ActionWebhook webhook;
    private final IntegrationRepository integrations;
    private final IntegrationGateway gateway;
    private final UsageMeter usage;
    private final UsageAllowance allowance;
    private final ActionMetrics metrics;
    private final Duration confirmationTtl;
    private final TransactionTemplate transactions;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    ActionRunner(ActionRepository actions, ActionRunRepository runs, ActionService settings, ActionWebhook webhook,
            IntegrationRepository integrations, IntegrationGateway gateway, UsageMeter usage,
            UsageAllowance allowance, ActionMetrics metrics, ActionSettings actionSettings,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.actions = actions;
        this.runs = runs;
        this.settings = settings;
        this.webhook = webhook;
        this.integrations = integrations;
        this.gateway = gateway;
        this.usage = usage;
        this.allowance = allowance;
        this.metrics = metrics;
        this.confirmationTtl = actionSettings.confirmationTtl();
        this.transactions = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    @Override
    public List<ActionDefinition> available() {
        return readOnly.execute(status -> {
            List<ActionDefinition> available = new ArrayList<>();
            actions.all().stream().filter(Action::enabled).map(Action::definition).forEach(available::add);
            integrations.all().stream()
                    .filter(Integration::enabled)
                    .flatMap(integration -> IntegrationAction.of(integration.provider()).stream())
                    .map(IntegrationAction::definition)
                    .forEach(available::add);
            return available;
        });
    }

    @Override
    public ActionOutcome invoke(ActionInvocation invocation) {
        return metrics.counted(attempt(invocation));
    }

    private ActionOutcome attempt(ActionInvocation invocation) {
        Instant now = clock.instant();
        UUID runId = Ids.newId();
        Optional<Target> found = readOnly.execute(status -> target(invocation.actionName()));
        if (found.isEmpty()) {
            return reject(ActionRun.rejected(runId, null, invocation.actionName(), invocation.actionName(),
                    invocation.conversationId(), invocation.messageId(), Map.of(), "Unknown action", now),
                    "There is no action named " + invocation.actionName() + "; use only the tools provided.");
        }
        Target action = found.get();
        Map<String, Object> arguments;
        try {
            arguments = ActionArguments.validate(action.parameters(), invocation.arguments());
        } catch (InvalidActionArgumentsException e) {
            return reject(ActionRun.rejected(runId, action.id(), action.name(), action.title(),
                    invocation.conversationId(), invocation.messageId(), Map.of(), e.getMessage(), now),
                    "Nothing was sent, the arguments are not valid: " + e.getMessage() + ".");
        }
        if (!Boolean.TRUE.equals(readOnly.execute(status -> allowance.allows(UsageMetric.ACTION_RUNS)))) {
            return reject(ActionRun.rejected(runId, action.id(), action.name(), action.title(),
                    invocation.conversationId(), invocation.messageId(), arguments, LIMIT_REACHED, now),
                    "Nothing was sent: this business reached its monthly limit of actions. Tell the customer it "
                            + "cannot be done right now and that they can ask for a person.");
        }

        if (action.requiresConfirmation()) {
            ActionRun run = ActionRun.pending(runId, action.id(), action.name(), action.title(),
                    invocation.conversationId(), invocation.messageId(), arguments, confirmationTtl, now);
            transactions.executeWithoutResult(status -> runs.insert(run));
            return new ActionOutcome(runId, run.status(), action.title(), arguments, PENDING_FOR_MODEL, null,
                    run.expiresAt());
        }

        WebhookResult result = call(action, runId, invocation.conversationId(), invocation.channel(),
                invocation.contactId(), arguments);
        ActionRun run = ActionRun.executed(runId, action.id(), action.name(), action.title(),
                invocation.conversationId(), invocation.messageId(), arguments, now, result, clock.instant());
        transactions.executeWithoutResult(status -> {
            runs.insert(run);
            usage.record(run.finishedAt(), UsageMetric.ACTION_RUNS, action.name(), 1);
        });
        return outcome(run, result);
    }

    @Override
    public ActionOutcome confirm(UUID runId, UUID conversationId, String channel, String contactId) {
        return metrics.counted(runConfirmed(runId, conversationId, channel, contactId));
    }

    private ActionOutcome runConfirmed(UUID runId, UUID conversationId, String channel, String contactId) {
        Instant now = clock.instant();
        Confirmed confirmed = transactions.execute(status -> {
            ActionRun run = runIn(runId, conversationId);
            boolean inTime = run.confirm(now);
            Optional<Target> action = inTime ? targetOf(run) : Optional.empty();
            if (inTime && action.isEmpty()) {
                // Deleted, disabled or disconnected while the visitor was deciding: nothing is sent
                run.finish(WebhookResult.unreachable("The action was deleted or disabled", 0), now);
            } else if (inTime && !allowance.allows(UsageMetric.ACTION_RUNS)) {
                run.finish(WebhookResult.unreachable(LIMIT_REACHED, 0), now);
                action = Optional.empty();
            }
            runs.update(run);
            return new Confirmed(run, action.orElse(null));
        });
        ActionRun run = confirmed.run();
        if (run.status() != ActionRunStatus.RUNNING) {
            return outcome(run, null);
        }

        WebhookResult result = call(confirmed.action(), runId, conversationId, channel, contactId, run.arguments());
        ActionRun finished = transactions.execute(status -> {
            ActionRun current = runs.findByIdForUpdate(runId).orElseThrow(ActionRunNotFoundException::new);
            current.finish(result, clock.instant());
            runs.update(current);
            usage.record(current.finishedAt(), UsageMetric.ACTION_RUNS, current.actionName(), 1);
            return current;
        });
        return outcome(finished, result);
    }

    @Override
    public ActionOutcome cancel(UUID runId, UUID conversationId) {
        ActionRun run = transactions.execute(status -> {
            ActionRun found = runIn(runId, conversationId);
            found.cancel(clock.instant());
            runs.update(found);
            return found;
        });
        return metrics.counted(outcome(run, null));
    }

    @Override
    public List<ActionRunView> runsOf(UUID conversationId) {
        return readOnly.execute(status -> runs.byConversation(conversationId).stream().map(ActionRun::view).toList());
    }

    private ActionRun runIn(UUID runId, UUID conversationId) {
        return runs.findByIdForUpdate(runId)
                .filter(run -> run.conversationId().equals(conversationId))
                .orElseThrow(ActionRunNotFoundException::new);
    }

    // A workspace's own action wins over an integration's; their names cannot clash anyway
    private Optional<Target> target(String name) {
        Optional<Target> own = actions.all().stream()
                .filter(action -> action.enabled() && action.name().equals(name))
                .findFirst()
                .map(Target::own);
        if (own.isPresent()) {
            return own;
        }
        return IntegrationAction.ofName(name).flatMap(action -> integrations.find(action.provider())
                .filter(Integration::enabled)
                .map(integration -> Target.integration(action, integration)));
    }

    private Optional<Target> targetOf(ActionRun run) {
        if (run.actionId() != null) {
            return actions.findById(run.actionId()).filter(Action::enabled).map(Target::own);
        }
        return IntegrationAction.ofName(run.actionName()).flatMap(action -> integrations.find(action.provider())
                .filter(Integration::enabled)
                .map(integration -> Target.integration(action, integration)));
    }

    private WebhookResult call(Target action, UUID runId, UUID conversationId, String channel, String contactId,
            Map<String, Object> arguments) {
        if (action.integration() != null) {
            WebhookResult result = gateway.run(action.integration(), action.integrationAction(),
                    new IntegrationCall(runId, conversationId, channel, contactId, arguments));
            metrics.called(action.integration().provider().pathName(), result);
            return result;
        }
        String secret = transactions.execute(status -> settings.signingSecret().value());
        WebhookResult result = webhook.call(new WebhookCall(action.own().endpointUrl(), secret, runId, action.name(),
                TenantContext.requireWorkspaceId(), conversationId, channel, contactId, arguments, clock.instant()));
        metrics.called(ActionMetrics.WEBHOOK, result);
        return result;
    }

    private ActionOutcome reject(ActionRun run, String forModel) {
        transactions.executeWithoutResult(status -> runs.insert(run));
        return new ActionOutcome(run.id(), run.status(), run.actionTitle(), run.arguments(), forModel, null, null);
    }

    private static ActionOutcome outcome(ActionRun run, WebhookResult result) {
        String forModel = switch (run.status()) {
            case SUCCEEDED -> "The business's system answered:\n"
                    + (run.result() == null || run.result().isBlank() ? "(an empty answer)" : run.result());
            case FAILED -> run.httpStatus() == null
                    ? "The business's system could not be reached. Tell the customer it could not be done right now "
                            + "and to try again later."
                    : "The business's system answered with error " + run.httpStatus()
                            + (run.result() == null || run.result().isBlank() ? "" : ":\n" + run.result())
                            + "\nTell the customer it could not be done, without technical details.";
            case CANCELLED -> "The customer cancelled the action; nothing was done.";
            case EXPIRED -> "The confirmation expired; nothing was done.";
            case PENDING_CONFIRMATION -> PENDING_FOR_MODEL;
            case REJECTED -> "Nothing was sent: " + run.error();
            case RUNNING -> "The action is still running.";
        };
        return new ActionOutcome(run.id(), run.status(), run.actionTitle(), run.arguments(), forModel,
                result == null ? null : result.message(), run.expiresAt());
    }

    private record Confirmed(ActionRun run, Target action) {
    }

    /**
     * What an action name leads to: one of the workspace's webhooks, or an integration's action.
     */
    private record Target(Action own, IntegrationAction integrationAction, Integration integration) {

        static Target own(Action action) {
            return new Target(action, null, null);
        }

        static Target integration(IntegrationAction action, Integration integration) {
            return new Target(null, action, integration);
        }

        // Runs keep the id of the workspace's own action; an integration's has none
        UUID id() {
            return own == null ? null : own.id();
        }

        String name() {
            return own == null ? integrationAction.actionName() : own.name();
        }

        String title() {
            return own == null ? integrationAction.title() : own.title();
        }

        List<ActionParameter> parameters() {
            return own == null ? integrationAction.parameters() : own.parameters();
        }

        boolean requiresConfirmation() {
            return own == null ? integrationAction.requiresConfirmation() : own.requiresConfirmation();
        }
    }
}
