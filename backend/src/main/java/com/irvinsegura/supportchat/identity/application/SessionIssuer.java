package com.irvinsegura.supportchat.identity.application;

import com.irvinsegura.supportchat.identity.domain.model.RefreshToken;
import com.irvinsegura.supportchat.identity.domain.port.AccessTokenIssuer;
import com.irvinsegura.supportchat.identity.domain.port.AccessTokenIssuer.IssuedAccessToken;
import com.irvinsegura.supportchat.identity.domain.port.RefreshTokenRepository;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.shared.domain.SecretTokens;
import com.irvinsegura.supportchat.workspaces.Membership;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues an access token and the next refresh token of a login family. Callers run it inside their transaction.
 */
@Component
class SessionIssuer {

    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    SessionIssuer(AccessTokenIssuer accessTokens, RefreshTokenRepository refreshTokens, Clock clock,
            @Value("${supportchat.identity.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    AuthSession startFamily(UUID userId, Membership membership) {
        return issue(userId, membership, Ids.newId());
    }

    AuthSession continueFamily(UUID userId, Membership membership, UUID familyId) {
        return issue(userId, membership, familyId);
    }

    private AuthSession issue(UUID userId, Membership membership, UUID familyId) {
        Instant now = clock.instant();
        String refreshToken = SecretTokens.generate();
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        refreshTokens.insert(new RefreshToken(Ids.newId(), userId, membership.workspaceId(), familyId,
                SecretTokens.hash(refreshToken), refreshExpiresAt, null, null), now);
        IssuedAccessToken access = accessTokens.issue(userId, membership.workspaceId(), membership.role());
        return new AuthSession(access.value(), access.expiresAt(), refreshToken, refreshExpiresAt, userId,
                membership.workspaceId(), membership.role());
    }
}
