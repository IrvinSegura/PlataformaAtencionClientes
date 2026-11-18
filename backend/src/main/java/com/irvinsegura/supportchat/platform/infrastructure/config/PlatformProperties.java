package com.irvinsegura.supportchat.platform.infrastructure.config;

import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param adminUserIds the users who run the platform; none by default, which leaves its administration closed
 */
@ConfigurationProperties("supportchat.platform")
public record PlatformProperties(Set<UUID> adminUserIds) {

    public PlatformProperties {
        adminUserIds = adminUserIds == null ? Set.of() : Set.copyOf(adminUserIds);
    }
}
