package com.irvinsegura.supportchat.identity.infrastructure.security;

import com.irvinsegura.supportchat.identity.OperatorClaims;
import com.irvinsegura.supportchat.identity.domain.port.AccessTokenIssuer;
import com.irvinsegura.supportchat.identity.infrastructure.config.IdentityProperties;
import com.irvinsegura.supportchat.shared.domain.Ids;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final IdentityProperties properties;
    private final Clock clock;

    JwtAccessTokenIssuer(JwtEncoder encoder, IdentityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issue(UUID userId, UUID workspaceId, WorkspaceRole role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        // Nothing personal goes in: a JWT is only signed, so anyone holding it can read it
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(userId.toString())
                .id(Ids.newId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(OperatorClaims.WORKSPACE, workspaceId.toString())
                .claim(OperatorClaims.ROLES, List.of(role.name()))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt);
    }
}
