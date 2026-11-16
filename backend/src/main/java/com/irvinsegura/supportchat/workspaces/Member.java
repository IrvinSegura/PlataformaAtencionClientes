package com.irvinsegura.supportchat.workspaces;

import java.time.Instant;
import java.util.UUID;

public record Member(UUID userId, WorkspaceRole role, Instant joinedAt) {
}
