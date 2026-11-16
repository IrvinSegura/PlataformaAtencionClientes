package com.irvinsegura.supportchat.identity.web;

import com.irvinsegura.supportchat.identity.application.AuthSession;
import com.irvinsegura.supportchat.workspaces.WorkspaceRole;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Turns a session into a response: the access token in the body, the refresh token in its HttpOnly cookie.
 */
@Component
class SessionResponses {

    /**
     * Required on the endpoints that read the refresh token cookie. A cross-site form cannot add a custom header,
     * and a cross-site script cannot either without a CORS preflight that only the panel's origins pass.
     */
    static final String CSRF_HEADER = "X-CSRF-Protection";

    private final RefreshTokenCookies cookies;

    SessionResponses(RefreshTokenCookies cookies) {
        this.cookies = cookies;
    }

    ResponseEntity<SessionResponse> of(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        cookies.issue(session.refreshToken(), session.refreshTokenExpiresAt()).toString())
                // Tokens must not be stored by browsers or proxies
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new SessionResponse(session.accessToken(), session.accessTokenExpiresAt(), session.userId(),
                        session.workspaceId(), session.role()));
    }

    ResponseEntity<Void> cleared() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    String refreshTokenFrom(HttpServletRequest request) {
        Cookie[] all = request.getCookies();
        if (all == null) {
            return null;
        }
        return Arrays.stream(all)
                .filter(cookie -> cookie.getName().equals(cookies.name()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    record SessionResponse(String accessToken, Instant accessTokenExpiresAt, UUID userId, UUID workspaceId,
            WorkspaceRole role) {
    }
}
