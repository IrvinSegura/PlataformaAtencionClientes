package com.irvinsegura.supportchat.identity.web;

import com.irvinsegura.supportchat.identity.OperatorClaims;
import com.irvinsegura.supportchat.shared.tenancy.TenantContext;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is making the request, read from the access token only.
 */
record CurrentOperator(UUID userId, UUID workspaceId, WorkspaceRole role) {

    static CurrentOperator of(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(OperatorClaims.ROLES);
        return new CurrentOperator(UUID.fromString(jwt.getSubject()), TenantContext.requireWorkspaceId(),
                WorkspaceRole.valueOf(roles.getFirst()));
    }
}
