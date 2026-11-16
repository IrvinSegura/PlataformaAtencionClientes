package com.irvinsegura.supportchat.identity.domain.model;

import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import java.util.UUID;

/**
 * Who may manage the team. The owner manages everyone; an admin brings in and removes agents, so an admin can never
 * make or unmake another admin. Agents manage nobody. Nobody removes themselves here or touches the owner.
 */
public final class MemberPolicy {

    private MemberPolicy() {
    }

    public static void checkInvite(WorkspaceRole actor, WorkspaceRole invitedRole) {
        if (actor == WorkspaceRole.OWNER && invitedRole != WorkspaceRole.OWNER) {
            return;
        }
        if (actor == WorkspaceRole.ADMIN && invitedRole == WorkspaceRole.AGENT) {
            return;
        }
        throw new MemberActionNotAllowedException("Your role cannot invite someone as " + invitedRole);
    }

    public static void checkRoleChange(WorkspaceRole actor) {
        if (actor != WorkspaceRole.OWNER) {
            throw new MemberActionNotAllowedException("Only the owner changes roles");
        }
    }

    public static void checkRemoval(UUID actorId, WorkspaceRole actor, UUID targetId, WorkspaceRole target) {
        if (actorId.equals(targetId)) {
            throw new MemberActionNotAllowedException("You cannot remove yourself");
        }
        boolean allowed = switch (actor) {
            case OWNER -> target != WorkspaceRole.OWNER;
            case ADMIN -> target == WorkspaceRole.AGENT;
            case AGENT -> false;
        };
        if (!allowed) {
            throw new MemberActionNotAllowedException("Your role cannot remove this member");
        }
    }
}
