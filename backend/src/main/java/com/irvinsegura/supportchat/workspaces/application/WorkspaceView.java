package com.irvinsegura.supportchat.workspaces.application;

import com.irvinsegura.supportchat.workspaces.WorkspaceSuspension;
import com.irvinsegura.supportchat.workspaces.domain.model.AllowedOrigin;
import com.irvinsegura.supportchat.workspaces.domain.model.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param suspension why whoever runs the platform suspended the workspace, or null while it is not
 */
public record WorkspaceView(UUID id, String name, String widgetKey, List<String> allowedOrigins, String botName,
        String welcomeMessage, Instant createdAt, WorkspaceSuspension suspension) {

    static WorkspaceView of(Workspace workspace, WorkspaceSuspension suspension) {
        return new WorkspaceView(workspace.id(), workspace.name(), workspace.widgetKey().value(),
                workspace.allowedOrigins().stream().map(AllowedOrigin::value).toList(), workspace.botName(),
                workspace.welcomeMessage(), workspace.createdAt(), suspension);
    }
}
