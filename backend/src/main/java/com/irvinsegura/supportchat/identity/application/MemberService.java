package com.irvinsegura.supportchat.identity.application;

import com.irvinsegura.supportchat.identity.domain.model.MemberPolicy;
import com.irvinsegura.supportchat.identity.domain.model.User;
import com.irvinsegura.supportchat.identity.domain.port.UserRepository;
import com.irvinsegura.supportchat.workspaces.Member;
import com.irvinsegura.supportchat.workspaces.MemberNotFoundException;
import com.irvinsegura.supportchat.workspaces.WorkspaceMembership;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import com.irvinsegura.supportchat.workspaces.Workspaces;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The team of a workspace. A removed member's refresh token stops working at once (refresh checks the membership);
 * an access token already issued lasts until it expires, at most 15 minutes.
 */
@Service
public class MemberService {

    private final Workspaces workspaces;
    private final UserRepository users;

    MemberService(Workspaces workspaces, UserRepository users) {
        this.workspaces = workspaces;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(UUID workspaceId) {
        List<Member> members = workspaces.members(workspaceId);
        Map<UUID, User> byId = users.findAllById(members.stream().map(Member::userId).toList()).stream()
                .collect(Collectors.toMap(User::id, Function.identity()));
        return members.stream()
                .filter(member -> byId.containsKey(member.userId()))
                .map(member -> {
                    User user = byId.get(member.userId());
                    return new MemberView(user.id(), user.email().value(), user.displayName(), member.role(),
                            member.joinedAt());
                })
                .toList();
    }

    @Transactional
    public void changeRole(UUID workspaceId, WorkspaceRole actorRole, UUID memberId, WorkspaceRole role) {
        MemberPolicy.checkRoleChange(actorRole);
        workspaces.changeRole(workspaceId, memberId, role);
    }

    @Transactional
    public void remove(UUID workspaceId, UUID actorId, WorkspaceRole actorRole, UUID memberId) {
        WorkspaceRole target = workspaces.membershipOf(memberId, workspaceId)
                .orElseThrow(MemberNotFoundException::new)
                .role();
        MemberPolicy.checkRemoval(actorId, actorRole, memberId, target);
        workspaces.removeMember(workspaceId, memberId);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMembership> workspacesOf(UUID userId) {
        return workspaces.membershipsOf(userId);
    }

    public record MemberView(UUID userId, String email, String displayName, WorkspaceRole role, Instant joinedAt) {
    }
}
