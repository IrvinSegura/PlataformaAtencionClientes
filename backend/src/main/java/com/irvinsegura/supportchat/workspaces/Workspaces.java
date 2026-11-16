package com.irvinsegura.supportchat.workspaces;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask about workspaces.
 */
public interface Workspaces {

    /**
     * Creates a workspace with the given user as its owner.
     */
    Membership createWithOwner(String workspaceName, UUID ownerUserId);

    /**
     * The workspace a user signs in to: their oldest membership.
     */
    Optional<Membership> defaultMembershipOf(UUID userId);

    /**
     * The user's current membership in a workspace, e.g. to check it still exists when a session is refreshed.
     */
    Optional<Membership> membershipOf(UUID userId, UUID workspaceId);

    /**
     * Every workspace the user belongs to.
     */
    List<WorkspaceMembership> membershipsOf(UUID userId);

    /**
     * Members of a workspace, in the order they joined.
     */
    List<Member> members(UUID workspaceId);

    /**
     * Adds a user to a workspace; a user who already belongs keeps their current role.
     *
     * @return the user's membership after the call
     */
    Membership addMember(UUID workspaceId, UUID userId, WorkspaceRole role);

    /**
     * @throws com.irvinsegura.supportchat.workspaces.domain.model.OwnerIsPermanentException for the owner's role, or
     *         to make someone owner
     */
    void changeRole(UUID workspaceId, UUID userId, WorkspaceRole role);

    /**
     * @throws com.irvinsegura.supportchat.workspaces.domain.model.OwnerIsPermanentException for the owner
     */
    void removeMember(UUID workspaceId, UUID userId);

    /**
     * The workspace behind a public widget key, if the key is current.
     */
    Optional<WidgetWorkspace> findByWidgetKey(String widgetKey);

    /**
     * How the assistant presents itself in a workspace.
     */
    AssistantProfile assistantProfile(UUID workspaceId);

    /**
     * Every workspace of the platform, newest first, for its administration.
     *
     * @param query part of the name, or null for all of them
     */
    WorkspacePage page(String query, int page, int size);

    Optional<WorkspaceSummary> summary(UUID workspaceId);

    /**
     * Why the workspace is suspended, or empty while it is not.
     */
    Optional<WorkspaceSuspension> suspension(UUID workspaceId);

    /**
     * Stops the workspace's assistant until it is reinstated; a suspended one only gets its reason changed.
     *
     * @throws com.irvinsegura.supportchat.workspaces.domain.model.WorkspaceNotFoundException for an unknown workspace
     */
    WorkspaceSuspension suspend(UUID workspaceId, String reason, UUID suspendedBy);

    /**
     * @throws com.irvinsegura.supportchat.workspaces.domain.model.WorkspaceNotFoundException for an unknown workspace
     */
    void reinstate(UUID workspaceId);

    record WorkspacePage(List<WorkspaceSummary> workspaces, long total) {
    }
}
