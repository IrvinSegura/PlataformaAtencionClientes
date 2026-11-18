package com.irvinsegura.supportchat.platform.application;

import com.irvinsegura.supportchat.billing.WorkspacePlan;
import com.irvinsegura.supportchat.billing.WorkspacePlans;
import com.irvinsegura.supportchat.conversations.ConversationActivity;
import com.irvinsegura.supportchat.identity.OperatorDirectory;
import com.irvinsegura.supportchat.platform.domain.model.PlatformWorkspaceNotFoundException;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import com.irvinsegura.supportchat.workspaces.WorkspaceSummary;
import com.irvinsegura.supportchat.workspaces.WorkspaceSuspension;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Every workspace of the platform with its plan and activity. Deliberately not transactional: what each workspace
 * keeps behind row-level security is read in a transaction of its own, bound to that workspace.
 */
@Service
public class PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformService.class);
    private static final Duration ACTIVITY_WINDOW = Duration.ofDays(30);

    private final Workspaces workspaces;
    private final WorkspacePlans plans;
    private final ConversationActivity activity;
    private final OperatorDirectory operators;
    private final Clock clock;

    PlatformService(Workspaces workspaces, WorkspacePlans plans, ConversationActivity activity,
            OperatorDirectory operators, Clock clock) {
        this.workspaces = workspaces;
        this.plans = plans;
        this.activity = activity;
        this.operators = operators;
        this.clock = clock;
    }

    /**
     * @param query part of the workspace's name, or null for all of them
     */
    public PlatformWorkspacePage page(String query, int page, int size) {
        Workspaces.WorkspacePage found = workspaces.page(query, page, size);
        return new PlatformWorkspacePage(found.workspaces().stream().map(this::describe).toList(), found.total());
    }

    public PlatformWorkspace get(UUID workspaceId) {
        return workspaces.summary(workspaceId).map(this::describe).orElseThrow(PlatformWorkspaceNotFoundException::new);
    }

    public PlatformWorkspace suspend(UUID workspaceId, String reason, UUID adminId) {
        WorkspaceSuspension suspension = workspaces.suspend(workspaceId, reason, adminId);
        log.warn("Platform administrator {} suspended workspace {}: {}", adminId, workspaceId, suspension.reason());
        return get(workspaceId);
    }

    public PlatformWorkspace reinstate(UUID workspaceId, UUID adminId) {
        workspaces.reinstate(workspaceId);
        log.warn("Platform administrator {} reinstated workspace {}", adminId, workspaceId);
        return get(workspaceId);
    }

    private PlatformWorkspace describe(WorkspaceSummary workspace) {
        Instant since = clock.instant().minus(ACTIVITY_WINDOW);
        WorkspacePlan plan = TenantContext.callAs(workspace.id(), plans::current);
        ConversationActivity.Activity recent = TenantContext.callAs(workspace.id(), () -> activity.since(since));
        UUID owner = workspace.ownerUserId();
        String ownerEmail = owner == null ? null : operators.emailOf(owner).orElse(null);
        String ownerName = ownerEmail == null ? null : operators.displayNameOf(owner);
        return new PlatformWorkspace(workspace.id(), workspace.name(), workspace.createdAt(), workspace.members(),
                ownerEmail, ownerName, plan, recent.conversations(), recent.lastActivityAt(),
                workspace.suspension());
    }

    public record PlatformWorkspacePage(List<PlatformWorkspace> items, long total) {
    }
}
