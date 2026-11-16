package com.irvinsegura.supportchat.workspaces;

import java.time.Instant;
import java.util.UUID;

/**
 * A workspace as the platform administration lists it.
 *
 * @param ownerUserId the member who owns it
 * @param suspension  why it is suspended, or null while it is not
 */
public record WorkspaceSummary(UUID id, String name, Instant createdAt, int members, UUID ownerUserId,
        WorkspaceSuspension suspension) {
}
