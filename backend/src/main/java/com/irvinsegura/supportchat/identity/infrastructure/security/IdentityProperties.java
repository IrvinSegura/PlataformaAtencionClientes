package com.irvinsegura.supportchat.identity.infrastructure.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("supportchat.identity")
public record IdentityProperties(
        @Valid @NotNull Jwt jwt,
        @NotNull Duration refreshTokenTtl,
        @Min(10) @Max(16) int passwordHashStrength,
        @Valid @NotNull Lockout lockout,
        @Valid @NotNull RefreshTokenCookie refreshTokenCookie) {

    /**
     * @param privateKeyLocation PKCS#8 PEM resource that signs tokens, e.g. {@code file:/run/secrets/jwt.key}
     * @param publicKeyLocation  X.509 PEM resource that verifies them
     */
    public record Jwt(@NotBlank String issuer, @NotNull Duration accessTokenTtl, String privateKeyLocation,
            String publicKeyLocation, boolean generateKeysIfMissing) {
    }

    public record Lockout(@Min(1) int maxFailedAttempts, @NotNull Duration duration) {
    }

    public record RefreshTokenCookie(@NotBlank String name, boolean secure, @NotBlank String sameSite) {
    }
}
