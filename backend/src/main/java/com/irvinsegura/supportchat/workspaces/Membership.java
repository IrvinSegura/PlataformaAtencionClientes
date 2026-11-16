package com.irvinsegura.supportchat.workspaces;

import java.util.UUID;

public record Membership(UUID workspaceId, WorkspaceRole role) {
}
