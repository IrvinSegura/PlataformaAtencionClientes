package com.irvinsegura.supportchat.workspaces.application;

import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.workspaces.AssistantProfile;
import com.irvinsegura.supportchat.workspaces.Member;
import com.irvinsegura.supportchat.workspaces.MemberNotFoundException;
import com.irvinsegura.supportchat.workspaces.Membership;
import com.irvinsegura.supportchat.workspaces.WidgetWorkspace;
import com.irvinsegura.supportchat.workspaces.WorkspaceMembership;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import com.irvinsegura.supportchat.workspaces.domain.model.AllowedOrigin;
import com.irvinsegura.supportchat.workspaces.domain.model.OwnerIsPermanentException;
import com.irvinsegura.supportchat.workspaces.domain.model.WidgetKey;
import com.irvinsegura.supportchat.workspaces.domain.model.Workspace;
import com.irvinsegura.supportchat.workspaces.domain.model.WorkspaceNotFoundException;
import com.irvinsegura.supportchat.workspaces.domain.port.MemberRepository;
import com.irvinsegura.supportchat.workspaces.WorkspaceSummary;
import com.irvinsegura.supportchat.workspaces.WorkspaceSuspension;
import com.irvinsegura.supportchat.workspaces.domain.model.InvalidSuspensionReasonException;
import com.irvinsegura.supportchat.workspaces.domain.port.WorkspaceDirectory;
import com.irvinsegura.supportchat.workspaces.domain.port.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService implements Workspaces {

    private static final int MAX_SUSPENSION_REASON_LENGTH = 500;

    private final WorkspaceRepository workspaces;
    private final MemberRepository members;
    private final WorkspaceDirectory directory;
    private final Clock clock;

    WorkspaceService(WorkspaceRepository workspaces, MemberRepository members, WorkspaceDirectory directory,
            Clock clock) {
        this.workspaces = workspaces;
        this.members = members;
        this.directory = directory;
        this.clock = clock;
    }

    // Joins the caller's transaction, so a failed sign-up leaves neither a workspace nor a member behind
    @Override
    @Transactional
    public Membership createWithOwner(String workspaceName, UUID ownerUserId) {
        Instant now = clock.instant();
        Workspace workspace = Workspace.create(Ids.newId(), workspaceName, now);
        workspaces.insert(workspace);
        members.add(workspace.id(), ownerUserId, WorkspaceRole.OWNER, now);
        return new Membership(workspace.id(), WorkspaceRole.OWNER);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> defaultMembershipOf(UUID userId) {
        return members.findOldestOf(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> membershipOf(UUID userId, UUID workspaceId) {
        return members.find(userId, workspaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceMembership> membershipsOf(UUID userId) {
        return members.membershipsOf(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Member> members(UUID workspaceId) {
        return members.list(workspaceId);
    }

    @Override
    @Transactional
    public Membership addMember(UUID workspaceId, UUID userId, WorkspaceRole role) {
        if (role == WorkspaceRole.OWNER) {
            throw new OwnerIsPermanentException();
        }
        load(workspaceId);
        return members.find(userId, workspaceId).orElseGet(() -> {
            members.add(workspaceId, userId, role, clock.instant());
            return new Membership(workspaceId, role);
        });
    }

    @Override
    @Transactional
    public void changeRole(UUID workspaceId, UUID userId, WorkspaceRole role) {
        Membership current = members.find(userId, workspaceId).orElseThrow(MemberNotFoundException::new);
        if (role == WorkspaceRole.OWNER || current.role() == WorkspaceRole.OWNER) {
            throw new OwnerIsPermanentException();
        }
        members.changeRole(workspaceId, userId, role);
    }

    @Override
    @Transactional
    public void removeMember(UUID workspaceId, UUID userId) {
        Membership current = members.find(userId, workspaceId).orElseThrow(MemberNotFoundException::new);
        if (current.role() == WorkspaceRole.OWNER) {
            throw new OwnerIsPermanentException();
        }
        members.remove(workspaceId, userId);
    }

    @Override
    public Optional<WidgetWorkspace> findByWidgetKey(String widgetKey) {
        if (!WidgetKey.isWellFormed(widgetKey)) {
            return Optional.empty();
        }
        return workspaces.findByWidgetKey(new WidgetKey(widgetKey))
                .map(workspace -> new WidgetWorkspace(workspace.id(), workspace.botName(),
                        workspace.welcomeMessage(),
                        workspace.allowedOrigins().stream().map(AllowedOrigin::value).toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public AssistantProfile assistantProfile(UUID workspaceId) {
        Workspace workspace = load(workspaceId);
        return new AssistantProfile(workspace.name(), workspace.botName());
    }

    @Transactional(readOnly = true)
    public WorkspaceView get(UUID workspaceId) {
        return WorkspaceView.of(load(workspaceId), directory.suspension(workspaceId).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspacePage page(String query, int page, int size) {
        WorkspaceDirectory.Page result = directory.page(query == null || query.isBlank() ? null : query.strip(),
                page, size);
        return new WorkspacePage(result.workspaces(), result.total());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceSummary> summary(UUID workspaceId) {
        return directory.find(workspaceId);
    }

    // Joins the caller's transaction: the assistant checks it before every answer
    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspaceSuspension> suspension(UUID workspaceId) {
        return directory.suspension(workspaceId);
    }

    @Override
    @Transactional
    public WorkspaceSuspension suspend(UUID workspaceId, String reason, UUID suspendedBy) {
        load(workspaceId);
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty() || text.length() > MAX_SUSPENSION_REASON_LENGTH) {
            throw new InvalidSuspensionReasonException(MAX_SUSPENSION_REASON_LENGTH);
        }
        directory.suspend(workspaceId, text, suspendedBy, clock.instant());
        return directory.suspension(workspaceId).orElseThrow();
    }

    @Override
    @Transactional
    public void reinstate(UUID workspaceId) {
        load(workspaceId);
        directory.reinstate(workspaceId);
    }

    @Transactional
    public WorkspaceView changeWidgetSettings(UUID workspaceId, String botName, String welcomeMessage,
            List<String> allowedOrigins) {
        Workspace workspace = load(workspaceId);
        List<AllowedOrigin> origins = allowedOrigins.stream().map(AllowedOrigin::parse).toList();
        workspace.changeWidgetSettings(botName, welcomeMessage, origins, clock.instant());
        workspaces.update(workspace);
        workspaces.evict(workspace.widgetKey());
        return WorkspaceView.of(workspace, directory.suspension(workspaceId).orElse(null));
    }

    @Transactional
    public WorkspaceView rotateWidgetKey(UUID workspaceId) {
        Workspace workspace = load(workspaceId);
        WidgetKey previous = workspace.rotateWidgetKey(clock.instant());
        workspaces.update(workspace);
        workspaces.evict(previous);
        return WorkspaceView.of(workspace, directory.suspension(workspaceId).orElse(null));
    }

    private Workspace load(UUID workspaceId) {
        return workspaces.findById(workspaceId).orElseThrow(WorkspaceNotFoundException::new);
    }
}
