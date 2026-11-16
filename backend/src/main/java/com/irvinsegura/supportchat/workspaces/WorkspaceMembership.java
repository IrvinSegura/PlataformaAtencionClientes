package com.irvinsegura.supportchat.workspaces;

import java.util.UUID;

/**
 * A workspace a user belongs to, with its name, e.g. for choosing which one to work in.
 */
public record WorkspaceMembership(UUID workspaceId, String workspaceName, WorkspaceRole role) {
}
