package com.irvinsegura.supportchat.identity.application;

import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import java.time.Instant;
import java.util.UUID;

/**
 * What a successful sign-in, sign-up or refresh hands to the client. The refresh token is in clear here only; the
 * web layer puts it in an HttpOnly cookie and never in a response body.
 */
public record AuthSession(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UUID userId,
        UUID workspaceId,
        WorkspaceRole role) {
}
