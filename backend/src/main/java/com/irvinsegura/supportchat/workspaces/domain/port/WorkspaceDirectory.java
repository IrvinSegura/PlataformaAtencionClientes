package com.irvinsegura.supportchat.workspaces.domain.port;

import com.irvinsegura.supportchat.workspaces.WorkspaceSummary;
import com.irvinsegura.supportchat.workspaces.WorkspaceSuspension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every workspace of the platform, for its administration, and the ones it suspended.
 */
public interface WorkspaceDirectory {

    /**
     * Workspaces newest first.
     *
     * @param query part of the name, or null for all of them
     */
    Page page(String query, int page, int size);

    Optional<WorkspaceSummary> find(UUID workspaceId);

    Optional<WorkspaceSuspension> suspension(UUID workspaceId);

    /**
     * Suspends the workspace, or replaces the reason of a suspension already in place.
     */
    void suspend(UUID workspaceId, String reason, UUID suspendedBy, Instant now);

    /**
     * @return whether it was suspended
     */
    boolean reinstate(UUID workspaceId);

    record Page(List<WorkspaceSummary> workspaces, long total) {
    }
}
