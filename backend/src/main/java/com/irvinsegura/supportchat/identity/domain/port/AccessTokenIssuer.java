package com.irvinsegura.supportchat.identity.domain.port;

import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(UUID userId, UUID workspaceId, WorkspaceRole role);

    record IssuedAccessToken(String value, Instant expiresAt) {
    }
}
