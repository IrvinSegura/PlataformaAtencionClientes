package com.irvinsegura.supportchat.actions.application;

import com.irvinsegura.supportchat.actions.ActionParameter;
import com.irvinsegura.supportchat.actions.domain.model.Action;
import com.irvinsegura.supportchat.actions.domain.model.ActionLimitReachedException;
import com.irvinsegura.supportchat.actions.domain.model.ActionNotFoundException;
import com.irvinsegura.supportchat.actions.domain.port.ActionRepository;
import com.irvinsegura.supportchat.actions.domain.port.EndpointPolicy;
import com.irvinsegura.supportchat.actions.domain.port.SigningSecretRepository;
import com.irvinsegura.supportchat.actions.domain.port.SigningSecretRepository.SigningSecret;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.domain.SecretTokens;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets owners and administrators set up their workspace's actions and the secret their webhooks verify.
 */
@Service
public class ActionService {

    private static final String SECRET_PREFIX = "whsec_";

    private final ActionRepository actions;
    private final SigningSecretRepository secrets;
    private final EndpointPolicy endpoints;
    private final Clock clock;

    ActionService(ActionRepository actions, SigningSecretRepository secrets, EndpointPolicy endpoints, Clock clock) {
        this.actions = actions;
        this.secrets = secrets;
        this.endpoints = endpoints;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ActionView> list() {
        return actions.all().stream().map(ActionView::of).toList();
    }

    @Transactional
    public ActionView create(Action.Details details) {
        if (actions.count() >= Action.MAX_PER_WORKSPACE) {
            throw new ActionLimitReachedException();
        }
        Action action = Action.create(Ids.newId(), TenantContext.requireWorkspaceId(), details, clock.instant());
        endpoints.check(action.endpointUrl());
        actions.insert(action);
        return ActionView.of(action);
    }

    @Transactional
    public ActionView update(UUID actionId, Action.Details details) {
        Action action = actions.findById(actionId).orElseThrow(ActionNotFoundException::new);
        action.change(details, clock.instant());
        endpoints.check(action.endpointUrl());
        actions.update(action);
        return ActionView.of(action);
    }

    @Transactional
    public void delete(UUID actionId) {
        if (!actions.delete(actionId)) {
            throw new ActionNotFoundException();
        }
    }

    /**
     * The secret webhooks use to verify requests, created the first time someone asks for it.
     */
    @Transactional
    public SigningSecret signingSecret() {
        return secrets.find().orElseGet(this::newSecret);
    }

    /**
     * Replaces the secret; webhooks must switch to the new one, since requests are signed only with it from now on.
     */
    @Transactional
    public SigningSecret rotateSigningSecret() {
        return newSecret();
    }

    private SigningSecret newSecret() {
        SigningSecret secret = new SigningSecret(SECRET_PREFIX + SecretTokens.generate(), clock.instant());
        secrets.save(TenantContext.requireWorkspaceId(), secret);
        return secret;
    }

    public record ActionView(UUID id, String name, String title, String description, String endpointUrl,
            List<ActionParameter> parameters, boolean requiresConfirmation, boolean enabled, Instant createdAt,
            Instant updatedAt) {

        static ActionView of(Action action) {
            return new ActionView(action.id(), action.name(), action.title(), action.description(),
                    action.endpointUrl(), action.parameters(), action.requiresConfirmation(), action.enabled(),
                    action.createdAt(), action.updatedAt());
        }
    }
}
