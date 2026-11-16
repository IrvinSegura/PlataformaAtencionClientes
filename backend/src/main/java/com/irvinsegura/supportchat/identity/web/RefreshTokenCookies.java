package com.irvinsegura.supportchat.identity.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token lives in an HttpOnly cookie that JavaScript cannot read, so a cross-site scripting bug in the
 * panel can steal at most a 15-minute access token. The cookie is only sent to the auth endpoints.
 */
@Component
class RefreshTokenCookies {

    static final String PATH = "/api/v1/auth";

    private final String name;
    private final boolean secure;
    private final String sameSite;
    private final Clock clock;

    RefreshTokenCookies(
            @Value("${supportchat.identity.refresh-token-cookie.name}") String name,
            @Value("${supportchat.identity.refresh-token-cookie.secure}") boolean secure,
            @Value("${supportchat.identity.refresh-token-cookie.same-site}") String sameSite,
            Clock clock) {
        this.name = name;
        this.secure = secure;
        this.sameSite = sameSite;
        this.clock = clock;
    }

    String name() {
        return name;
    }

    ResponseCookie issue(String token, Instant expiresAt) {
        return base(token).maxAge(Duration.between(clock.instant(), expiresAt)).build();
    }

    ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(PATH);
    }
}
