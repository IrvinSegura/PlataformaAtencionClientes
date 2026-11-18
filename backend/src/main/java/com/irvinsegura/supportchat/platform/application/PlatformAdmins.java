package com.irvinsegura.supportchat.platform.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Who runs the platform. They are configured by user id rather than e-mail, since anyone can sign up with any
 * address; nobody becomes one through the API.
 */
public class PlatformAdmins {

    private final Set<UUID> userIds;

    public PlatformAdmins(Set<UUID> userIds) {
        this.userIds = Set.copyOf(userIds);
    }

    public boolean isAdmin(UUID userId) {
        return userId != null && userIds.contains(userId);
    }

    /**
     * Whether the operator behind the request runs the platform; used in {@code @PreAuthorize}.
     */
    public boolean allows(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwt && isAdmin(userIdOf(jwt.getToken().getSubject()));
    }

    private static UUID userIdOf(String subject) {
        try {
            return subject == null ? null : UUID.fromString(subject);
        } catch (IllegalArgumentException notAUser) {
            return null;
        }
    }
}
