package com.irvinsegura.supportchat.actions.application;

import com.irvinsegura.supportchat.actions.ActionOutcome;
import com.irvinsegura.supportchat.actions.domain.model.WebhookResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Business meters of the actions. Tags hold the run's status and who answered (a webhook or an integration's
 * provider), never an action's name: workspaces choose those, so they would grow the number of series without limit.
 */
@Component
class ActionMetrics {

    static final String WEBHOOK = "webhook";

    private final MeterRegistry registry;

    ActionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Counts what the assistant's request or the customer's answer ended in, and returns it. */
    ActionOutcome counted(ActionOutcome outcome) {
        registry.counter("supportchat.actions.runs", "status", outcome.status().name()).increment();
        return outcome;
    }

    /** Times one call to the business's webhook or to an integration's API. */
    void called(String target, WebhookResult result) {
        String outcome = result.succeeded() ? "success" : result.httpStatus() == null ? "unreachable" : "error";
        Timer.builder("supportchat.actions.calls")
                .description("Calls to the business's webhooks and to the integrations' APIs")
                .tag("target", target)
                .tag("outcome", outcome)
                .register(registry)
                .record(Duration.ofMillis(result.latencyMillis()));
    }
}
