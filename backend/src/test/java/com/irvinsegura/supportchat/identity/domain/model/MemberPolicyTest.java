package com.irvinsegura.supportchat.identity.domain.model;

import static com.irvinsegura.supportchat.workspaces.WorkspaceRole.ADMIN;
import static com.irvinsegura.supportchat.workspaces.WorkspaceRole.AGENT;
import static com.irvinsegura.supportchat.workspaces.WorkspaceRole.OWNER;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberPolicyTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID SOMEONE = UUID.randomUUID();

    @Test
    void theOwnerInvitesAdminsAndAgentsButNoOtherOwner() {
        assertThatCode(() -> MemberPolicy.checkInvite(OWNER, ADMIN)).doesNotThrowAnyException();
        assertThatCode(() -> MemberPolicy.checkInvite(OWNER, AGENT)).doesNotThrowAnyException();
        assertThatThrownBy(() -> MemberPolicy.checkInvite(OWNER, OWNER))
                .isInstanceOf(MemberActionNotAllowedException.class);
    }

    @Test
    void anAdminOnlyInvitesAgentsAndAnAgentNobody() {
        assertThatCode(() -> MemberPolicy.checkInvite(ADMIN, AGENT)).doesNotThrowAnyException();
        assertThatThrownBy(() -> MemberPolicy.checkInvite(ADMIN, ADMIN))
                .isInstanceOf(MemberActionNotAllowedException.class);
        assertThatThrownBy(() -> MemberPolicy.checkInvite(AGENT, AGENT))
                .isInstanceOf(MemberActionNotAllowedException.class);
    }

    @Test
    void membersAreRemovedFromAboveAndNeverByThemselves() {
        assertThatCode(() -> MemberPolicy.checkRemoval(ME, OWNER, SOMEONE, ADMIN)).doesNotThrowAnyException();
        assertThatCode(() -> MemberPolicy.checkRemoval(ME, ADMIN, SOMEONE, AGENT)).doesNotThrowAnyException();
        assertThatThrownBy(() -> MemberPolicy.checkRemoval(ME, ADMIN, SOMEONE, ADMIN))
                .isInstanceOf(MemberActionNotAllowedException.class);
        assertThatThrownBy(() -> MemberPolicy.checkRemoval(ME, ADMIN, SOMEONE, OWNER))
                .isInstanceOf(MemberActionNotAllowedException.class);
        assertThatThrownBy(() -> MemberPolicy.checkRemoval(ME, OWNER, ME, OWNER))
                .isInstanceOf(MemberActionNotAllowedException.class);
    }

    @Test
    void onlyTheOwnerChangesRoles() {
        assertThatCode(() -> MemberPolicy.checkRoleChange(OWNER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> MemberPolicy.checkRoleChange(ADMIN))
                .isInstanceOf(MemberActionNotAllowedException.class);
    }
}
