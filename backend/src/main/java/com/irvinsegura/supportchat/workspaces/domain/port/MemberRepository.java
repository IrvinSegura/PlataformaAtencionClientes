package com.irvinsegura.supportchat.workspaces.domain.port;

import com.irvinsegura.supportchat.workspaces.Member;
import com.irvinsegura.supportchat.workspaces.Membership;
import com.irvinsegura.supportchat.workspaces.WorkspaceMembership;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {

    void add(UUID workspaceId, UUID userId, WorkspaceRole role, Instant now);

    Optional<Membership> findOldestOf(UUID userId);

    Optional<Membership> find(UUID userId, UUID workspaceId);

    /**
     * Members in the order they joined.
     */
    List<Member> list(UUID workspaceId);

    /**
     * Workspaces the user belongs to, in the order they joined them.
     */
    List<WorkspaceMembership> membershipsOf(UUID userId);

    void changeRole(UUID workspaceId, UUID userId, WorkspaceRole role);

    void remove(UUID workspaceId, UUID userId);
}
