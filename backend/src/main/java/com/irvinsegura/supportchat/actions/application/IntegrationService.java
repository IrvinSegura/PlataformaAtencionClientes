package com.irvinsegura.supportchat.actions.application;

import com.irvinsegura.supportchat.actions.ActionDefinition;
import com.irvinsegura.supportchat.actions.domain.model.Integration;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationAction;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationNotFoundException;
import com.irvinsegura.supportchat.actions.domain.model.IntegrationProvider;
import com.irvinsegura.supportchat.actions.domain.port.IntegrationRepository;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets owners and administrators connect the integrations whose actions the assistant may use.
 */
@Service
public class IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);

    private final IntegrationRepository integrations;
    private final Clock clock;

    IntegrationService(IntegrationRepository integrations, Clock clock) {
        this.integrations = integrations;
        this.clock = clock;
    }

    /**
     * Every provider the platform knows, connected or not, with the actions it would give the assistant.
     */
    @Transactional(readOnly = true)
    public List<IntegrationView> list() {
        Map<IntegrationProvider, Integration> connected = integrations.all().stream()
                .collect(Collectors.toMap(Integration::provider, Function.identity()));
        return Arrays.stream(IntegrationProvider.values())
                .map(provider -> IntegrationView.of(provider, connected.get(provider)))
                .toList();
    }

    @Transactional
    public IntegrationView configure(IntegrationProvider provider, Integration.Settings settings) {
        Instant now = clock.instant();
        Integration integration = integrations.find(provider)
                .map(existing -> {
                    existing.change(settings, now);
                    return existing;
                })
                .orElseGet(() -> Integration.connect(TenantContext.requireWorkspaceId(), provider, settings, now));
        integrations.save(integration);
        log.info("Integration {} saved for workspace {}, enabled: {}", provider, integration.workspaceId(),
                integration.enabled());
        return IntegrationView.of(provider, integration);
    }

    @Transactional
    public void disconnect(IntegrationProvider provider) {
        if (!integrations.delete(provider)) {
            throw new IntegrationNotFoundException();
        }
    }

    /**
     * An integration as the panel shows it; the token is never sent back.
     *
     * @param account   the store or subdomain; null when not connected
     * @param connected whether the workspace saved credentials for it
     */
    public record IntegrationView(IntegrationProvider provider, boolean connected, @Nullable String account,
            @Nullable String username, boolean enabled, List<ActionDefinition> actions, @Nullable Instant updatedAt) {

        static IntegrationView of(IntegrationProvider provider, Integration integration) {
            List<ActionDefinition> actions = IntegrationAction.of(provider).stream()
                    .map(IntegrationAction::definition)
                    .toList();
            if (integration == null) {
                return new IntegrationView(provider, false, null, null, false, actions, null);
            }
            return new IntegrationView(provider, true, integration.account(), integration.username(),
                    integration.enabled(), actions, integration.updatedAt());
        }
    }
}
