package com.irvinsegura.supportchat.platform.application;

import com.irvinsegura.supportchat.billing.WorkspacePlan;
import com.irvinsegura.supportchat.workspaces.WorkspaceSuspension;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A workspace as those who run the platform see it.
 *
 * @param ownerEmail             how to reach its owner, or null if the account is gone
 * @param conversationsLastMonth conversations started in the last 30 days
 * @param lastActivityAt         the latest message of any of its conversations, or null if it never had one
 * @param suspension             why it is suspended, or null while it is not
 */
public record PlatformWorkspace(UUID id, String name, Instant createdAt, int members, @Nullable String ownerEmail,
        @Nullable String ownerName, WorkspacePlan plan, long conversationsLastMonth, @Nullable Instant lastActivityAt,
        @Nullable WorkspaceSuspension suspension) {
}
